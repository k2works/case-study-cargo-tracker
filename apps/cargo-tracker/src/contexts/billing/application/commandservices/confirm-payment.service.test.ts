import { describe, expect, it, beforeEach } from 'vitest';
import { ConfirmPaymentService, type EventPublisher } from './confirm-payment.service.js';
import type { InvoiceRepository } from '../../domain/repository/invoice-repository.js';
import type {
  PaymentConfirmation,
  PaymentGatewayPort,
} from '../outboundservices/acl/payment-gateway-port.js';
import { Invoice } from '../../domain/model/invoice.js';
import { BillingBookingId } from '../../domain/model/billing-booking-id.js';
import { BillingShipperId } from '../../domain/model/billing-shipper-id.js';
import { DiscountRate } from '../../domain/model/discount-rate.js';
import { Money } from '../../domain/model/money.js';
import { PaymentStatus } from '../../domain/model/payment-status.js';
import { BillingValidationError } from '../../domain/model/billing-validation-error.js';
import { PAYMENT_CONFIRMED_EVENT } from '../../../../shared/contracts/payment-confirmed.contract.js';

function makeInvoice(): Invoice {
  return Invoice.issue({
    invoiceNumber: 'INV-TEST01',
    cargoBookingId: BillingBookingId.of('booking-1'),
    shipperId: BillingShipperId.of('SHP-1', 'INDIVIDUAL'),
    baseAmount: Money.of(100_000, 'JPY'),
    discountRate: DiscountRate.zero(),
    issuedAt: new Date('2027-06-01T00:00:00Z'),
  });
}

class FakeInvoiceRepository implements InvoiceRepository {
  updated: Invoice | null = null;
  constructor(private readonly invoice: Invoice | null) {}
  async save(): Promise<void> {}
  async update(invoice: Invoice): Promise<void> {
    this.updated = invoice;
  }
  async findByInvoiceId(): Promise<Invoice | null> {
    return this.invoice;
  }
  async findByInvoiceNumber(): Promise<Invoice | null> {
    return this.invoice;
  }
  async findByBookingId(): Promise<Invoice | null> {
    return this.invoice;
  }
  async findAll(): Promise<Invoice[]> {
    return this.invoice ? [this.invoice] : [];
  }
}

class FakeGateway implements PaymentGatewayPort {
  calls = 0;
  constructor(private readonly result: PaymentConfirmation | null) {}
  async confirmPayment(): Promise<PaymentConfirmation | null> {
    this.calls += 1;
    return this.result;
  }
}

class FakePublisher implements EventPublisher {
  emitted: { event: string; payload: unknown }[] = [];
  emit(event: string, payload: unknown): void {
    this.emitted.push({ event, payload });
  }
}

describe('ConfirmPaymentService (US23)', () => {
  let publisher: FakePublisher;
  beforeEach(() => {
    publisher = new FakePublisher();
  });

  it('入金確認済みへ遷移し、決済手段・取引参照を保存し、精算完了イベントを発行する', async () => {
    const invoice = makeInvoice();
    const repo = new FakeInvoiceRepository(invoice);
    const gateway = new FakeGateway({
      paidAt: new Date('2027-06-10T00:00:00Z'),
      method: 'BANK_TRANSFER',
      transactionReference: 'TXN-1',
    });
    const service = new ConfirmPaymentService(repo, gateway, publisher);

    await service.confirm('INV-TEST01');

    expect(repo.updated?.paymentStatus).toBe(PaymentStatus.CONFIRMED);
    expect(repo.updated?.paymentMethod).toBe('BANK_TRANSFER');
    expect(repo.updated?.transactionReference).toBe('TXN-1');
    const events = publisher.emitted.filter((e) => e.event === PAYMENT_CONFIRMED_EVENT);
    expect(events).toHaveLength(1);
    expect(events[0].payload).toEqual({ bookingId: 'booking-1' });
  });

  it('存在しない請求書番号は検証エラー', async () => {
    const service = new ConfirmPaymentService(new FakeInvoiceRepository(null), new FakeGateway(null), publisher);
    await expect(service.confirm('INV-NONE')).rejects.toBeInstanceOf(BillingValidationError);
  });

  it('既に CONFIRMED の請求書は gateway を呼ばずに早期リターンする（二重確認の窓を閉じる・programmer#2）', async () => {
    const invoice = makeInvoice();
    invoice.confirmPayment(new Date('2027-06-05T00:00:00Z'), 'BANK_TRANSFER', 'TXN-0');
    const repo = new FakeInvoiceRepository(invoice);
    const gateway = new FakeGateway({
      paidAt: new Date('2027-06-10T00:00:00Z'),
      method: 'BANK_TRANSFER',
      transactionReference: 'TXN-1',
    });
    const service = new ConfirmPaymentService(repo, gateway, publisher);

    await service.confirm('INV-TEST01');

    expect(gateway.calls).toBe(0);
    expect(repo.updated).toBeNull();
    expect(publisher.emitted).toHaveLength(0);
  });

  it('未入金（決済機関が null）は検証エラーで状態を変えない', async () => {
    const invoice = makeInvoice();
    const repo = new FakeInvoiceRepository(invoice);
    const service = new ConfirmPaymentService(repo, new FakeGateway(null), publisher);
    await expect(service.confirm('INV-TEST01')).rejects.toBeInstanceOf(BillingValidationError);
    expect(repo.updated).toBeNull();
    expect(publisher.emitted).toHaveLength(0);
  });
});
