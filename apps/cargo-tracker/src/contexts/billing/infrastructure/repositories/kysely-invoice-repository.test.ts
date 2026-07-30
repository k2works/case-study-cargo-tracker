import { beforeEach, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { KyselyInvoiceRepository } from './kysely-invoice-repository.js';
import { Invoice } from '../../domain/model/invoice.js';
import { BillingBookingId } from '../../domain/model/billing-booking-id.js';
import { BillingShipperId } from '../../domain/model/billing-shipper-id.js';
import { Money } from '../../domain/model/money.js';
import { DiscountRate } from '../../domain/model/discount-rate.js';
import { PaymentStatus } from '../../domain/model/payment-status.js';

describe('KyselyInvoiceRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyInvoiceRepository;
  const bookingId = randomUUID();

  beforeEach(() => {
    db = createPgMemDatabase().db;
    repo = new KyselyInvoiceRepository(db);
  });

  function corporateInvoice(number = 'INV-0001', booking = bookingId): Invoice {
    return Invoice.issue({
      invoiceNumber: number,
      cargoBookingId: BillingBookingId.of(booking),
      shipperId: BillingShipperId.of('shipper-1', 'CORPORATE'),
      baseAmount: Money.of(1000, 'JPY'),
      discountRate: DiscountRate.of(0.3),
      issuedAt: new Date('2026-09-01T00:00:00Z'),
    });
  }

  it('発行した請求書を保存し booking_id で復元できる', async () => {
    await repo.save(corporateInvoice());
    const found = await repo.findByBookingId(bookingId);
    expect(found).not.toBeNull();
    expect(found!.invoiceNumber).toBe('INV-0001');
    expect(found!.finalAmount.amount.toString()).toBe('770');
    expect(found!.taxAmount.amount.toString()).toBe('70');
    expect(found!.paymentStatus).toBe(PaymentStatus.PENDING);
    // 明細（基本料金・割引・消費税）も復元される
    expect(found!.lineItems.length).toBeGreaterThanOrEqual(3);
    expect(found!.shipperId.isCorporate()).toBe(true);
  });

  it('同一 booking_id での二重請求は UNIQUE 制約で拒否される', async () => {
    await repo.save(corporateInvoice('INV-0001'));
    await expect(repo.save(corporateInvoice('INV-0002'))).rejects.toThrow();
  });

  it('入金確認済みへの更新で支払記録を追記する', async () => {
    const invoice = corporateInvoice();
    await repo.save(invoice);
    invoice.confirmPayment(new Date('2026-09-15T00:00:00Z'));
    await repo.update(invoice);

    const reloaded = await repo.findByBookingId(bookingId);
    expect(reloaded!.paymentStatus).toBe(PaymentStatus.CONFIRMED);

    const payments = await db.selectFrom('payment').selectAll().execute();
    expect(payments).toHaveLength(1);
    expect(payments[0].paidAmountValue).toBe(770);
    expect(payments[0].paymentMethod).toBe('BANK_TRANSFER');
  });

  it('findAll は全請求書を返す', async () => {
    await repo.save(corporateInvoice('INV-0001', randomUUID()));
    await repo.save(corporateInvoice('INV-0002', randomUUID()));
    const all = await repo.findAll();
    expect(all).toHaveLength(2);
  });
});
