import { describe, expect, it } from 'vitest';
import { Decimal } from 'decimal.js';
import { Invoice } from './invoice.js';
import { InvoiceId } from './invoice-id.js';
import { BillingBookingId } from './billing-booking-id.js';
import { BillingShipperId } from './billing-shipper-id.js';
import { Money } from './money.js';
import { DiscountRate } from './discount-rate.js';
import { InvoiceLineItem } from './invoice-line-item.js';
import { PaymentStatus } from './payment-status.js';
import { BillingValidationError } from './billing-validation-error.js';

function issueInvoice(
  overrides: Partial<{
    shipperType: string;
    discountRate: DiscountRate;
    baseAmount: Money;
    adjustments: InvoiceLineItem[];
    issuedAt: Date;
  }> = {},
): Invoice {
  return Invoice.issue({
    invoiceNumber: 'INV-0001',
    cargoBookingId: BillingBookingId.of('booking-1'),
    shipperId: BillingShipperId.of('shipper-1', overrides.shipperType ?? 'CORPORATE'),
    baseAmount: overrides.baseAmount ?? Money.of(1000, 'JPY'),
    adjustments: overrides.adjustments,
    discountRate: overrides.discountRate ?? DiscountRate.of(0.3),
    issuedAt: overrides.issuedAt ?? new Date('2026-09-01T00:00:00Z'),
  });
}

describe('Invoice 集約', () => {
  it('PENDING 状態で発行し、支払期限は発行日 + 30 日', () => {
    const invoice = issueInvoice();
    expect(invoice.paymentStatus).toBe(PaymentStatus.PENDING);
    expect(invoice.paidAt).toBeNull();
    expect(invoice.dueDate.toISOString()).toBe(new Date('2026-10-01T00:00:00Z').toISOString());
  });

  it('請求書番号が空ならエラー', () => {
    expect(() =>
      Invoice.issue({
        invoiceNumber: '   ',
        cargoBookingId: BillingBookingId.of('booking-1'),
        shipperId: BillingShipperId.of('shipper-1', 'CORPORATE'),
        baseAmount: Money.of(1000, 'JPY'),
        discountRate: DiscountRate.of(0.1),
        issuedAt: new Date('2026-09-01T00:00:00Z'),
      }),
    ).toThrow(BillingValidationError);
  });

  describe('calculateFinalAmount（割引・消費税）', () => {
    it('法人（割引 30%）: 割引後 700 + 消費税 70 = 770', () => {
      const invoice = issueInvoice({ shipperType: 'CORPORATE', discountRate: DiscountRate.of(0.3) });
      expect(invoice.finalAmount.amount.toString()).toBe('770');
      expect(invoice.taxAmount.amount.toString()).toBe('70');
    });

    it('個人: 割引率を持っていても割引は適用されない（1000 + 消費税 100 = 1100）', () => {
      const invoice = issueInvoice({ shipperType: 'INDIVIDUAL', discountRate: DiscountRate.of(0.3) });
      expect(invoice.finalAmount.amount.toString()).toBe('1100');
      expect(invoice.taxAmount.amount.toString()).toBe('100');
    });

    it('例外調整（補償費用）を加算する: (1000 + 500) 割引なし + 消費税 150 = 1650', () => {
      const invoice = issueInvoice({
        shipperType: 'INDIVIDUAL',
        adjustments: [InvoiceLineItem.of('補償費用', Money.of(500, 'JPY'))],
      });
      expect(invoice.finalAmount.amount.toString()).toBe('1650');
    });

    it('割引根拠と消費税を明細に積む（法人）', () => {
      const invoice = issueInvoice({ shipperType: 'CORPORATE', discountRate: DiscountRate.of(0.3) });
      const descriptions = invoice.lineItems.map((item) => item.description);
      expect(descriptions[0]).toBe('基本料金');
      expect(descriptions.some((d) => d.includes('法人割引'))).toBe(true);
      expect(descriptions.some((d) => d.includes('消費税'))).toBe(true);
    });

    it('個人は割引明細を積まない', () => {
      const invoice = issueInvoice({ shipperType: 'INDIVIDUAL' });
      expect(invoice.lineItems.some((item) => item.description.includes('割引'))).toBe(false);
    });
  });

  describe('confirmPayment（支払い確定）', () => {
    it('PENDING → CONFIRMED（paidAt 設定）', () => {
      const invoice = issueInvoice();
      const paidAt = new Date('2026-09-15T00:00:00Z');
      invoice.confirmPayment(paidAt);
      expect(invoice.paymentStatus).toBe(PaymentStatus.CONFIRMED);
      expect(invoice.paidAt).toEqual(paidAt);
    });

    it('OVERDUE → CONFIRMED', () => {
      const invoice = issueInvoice();
      invoice.markOverdue(new Date('2026-11-01T00:00:00Z'));
      expect(invoice.paymentStatus).toBe(PaymentStatus.OVERDUE);
      invoice.confirmPayment(new Date('2026-11-02T00:00:00Z'));
      expect(invoice.paymentStatus).toBe(PaymentStatus.CONFIRMED);
    });

    it('CONFIRMED からの再確認はエラー', () => {
      const invoice = issueInvoice();
      invoice.confirmPayment(new Date('2026-09-15T00:00:00Z'));
      expect(() => invoice.confirmPayment(new Date('2026-09-16T00:00:00Z'))).toThrow(
        BillingValidationError,
      );
    });
  });

  describe('markOverdue', () => {
    it('PENDING かつ期限超過なら OVERDUE へ遷移し true（初回のみ）', () => {
      const invoice = issueInvoice();
      expect(invoice.markOverdue(new Date('2026-10-02T00:00:00Z'))).toBe(true);
      expect(invoice.paymentStatus).toBe(PaymentStatus.OVERDUE);
      // 2 回目は既に OVERDUE のため false（通知不要）
      expect(invoice.markOverdue(new Date('2026-10-03T00:00:00Z'))).toBe(false);
    });

    it('期限内なら遷移せず false', () => {
      const invoice = issueInvoice();
      expect(invoice.markOverdue(new Date('2026-09-15T00:00:00Z'))).toBe(false);
      expect(invoice.paymentStatus).toBe(PaymentStatus.PENDING);
    });

    it('CONFIRMED には適用しない（false）', () => {
      const invoice = issueInvoice();
      invoice.confirmPayment(new Date('2026-09-15T00:00:00Z'));
      expect(invoice.markOverdue(new Date('2026-11-01T00:00:00Z'))).toBe(false);
      expect(invoice.paymentStatus).toBe(PaymentStatus.CONFIRMED);
    });
  });

  it('reconstruct で状態を復元できる', () => {
    const invoice = Invoice.reconstruct({
      invoiceId: InvoiceId.of('inv-1'),
      invoiceNumber: 'INV-0002',
      cargoBookingId: BillingBookingId.of('booking-2'),
      shipperId: BillingShipperId.of('shipper-2', 'CORPORATE'),
      baseAmount: Money.of(1000, 'JPY'),
      discountRate: DiscountRate.of(0.3),
      taxRate: new Decimal('0.10'),
      finalAmount: Money.of(770, 'JPY'),
      taxAmount: Money.of(70, 'JPY'),
      paymentStatus: PaymentStatus.CONFIRMED,
      issuedAt: new Date('2026-09-01T00:00:00Z'),
      dueDate: new Date('2026-10-01T00:00:00Z'),
      paidAt: new Date('2026-09-15T00:00:00Z'),
      lineItems: [InvoiceLineItem.of('基本料金', Money.of(1000, 'JPY'))],
    });
    expect(invoice.paymentStatus).toBe(PaymentStatus.CONFIRMED);
    expect(invoice.finalAmount.amount.toString()).toBe('770');
    expect(invoice.lineItems).toHaveLength(1);
  });
});
