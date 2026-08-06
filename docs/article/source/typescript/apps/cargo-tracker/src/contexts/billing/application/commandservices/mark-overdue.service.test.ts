import { describe, expect, it, beforeEach } from 'vitest';
import { MarkOverdueService } from './mark-overdue.service.js';
import type { InvoiceRepository } from '../../domain/repository/invoice-repository.js';
import type {
  BillingNotificationPort,
  BillingNotificationRequest,
} from '../outboundservices/acl/billing-notification-port.js';
import { Invoice } from '../../domain/model/invoice.js';
import { BillingBookingId } from '../../domain/model/billing-booking-id.js';
import { BillingShipperId } from '../../domain/model/billing-shipper-id.js';
import { Money } from '../../domain/model/money.js';
import { DiscountRate } from '../../domain/model/discount-rate.js';
import { NotificationType } from '../../../../shared/contracts/notification-type.js';

function pendingInvoice(number = 'INV-0001'): Invoice {
  return Invoice.issue({
    invoiceNumber: number,
    cargoBookingId: BillingBookingId.of('booking-1'),
    shipperId: BillingShipperId.of('SHP-1', 'INDIVIDUAL'),
    baseAmount: Money.of(1000, 'JPY'),
    discountRate: DiscountRate.zero(),
    issuedAt: new Date('2027-06-01T00:00:00Z'),
  });
}

class ListInvoiceRepository implements InvoiceRepository {
  updated: Invoice[] = [];
  constructor(private readonly invoices: Invoice[]) {}
  async save(): Promise<void> {}
  async update(invoice: Invoice): Promise<void> {
    this.updated.push(invoice);
  }
  async findByInvoiceId(): Promise<Invoice | null> {
    return null;
  }
  async findByInvoiceNumber(): Promise<Invoice | null> {
    return null;
  }
  async findByBookingId(): Promise<Invoice | null> {
    return null;
  }
  async findAll(): Promise<Invoice[]> {
    return this.invoices;
  }
}

class RecordingNotifier implements BillingNotificationPort {
  requests: BillingNotificationRequest[] = [];
  async notify(request: BillingNotificationRequest): Promise<void> {
    this.requests.push(request);
  }
}

// 支払期限（発行 + 30 日 = 2027-07-01）を過ぎた時刻
const afterDue = () => new Date('2027-07-15T00:00:00Z');

describe('MarkOverdueService (US23-5)', () => {
  let notifier: RecordingNotifier;
  beforeEach(() => {
    notifier = new RecordingNotifier();
  });

  it('PENDING かつ期限超過は OVERDUE へ更新し、初回のみ未払い通知する', async () => {
    const invoice = pendingInvoice();
    const repo = new ListInvoiceRepository([invoice]);
    const service = new MarkOverdueService(repo, notifier, afterDue);

    await service.sweep();

    expect(repo.updated).toHaveLength(1);
    expect(notifier.requests).toHaveLength(1);
    expect(notifier.requests[0].notificationType).toBe(NotificationType.PAYMENT_OVERDUE);
  });

  it('2 回目の sweep では既に OVERDUE のため通知しない', async () => {
    const invoice = pendingInvoice();
    const repo = new ListInvoiceRepository([invoice]);
    const service = new MarkOverdueService(repo, notifier, afterDue);

    await service.sweep();
    await service.sweep();

    expect(notifier.requests).toHaveLength(1);
  });

  it('CONFIRMED には適用しない（通知も更新もしない）', async () => {
    const invoice = pendingInvoice();
    invoice.confirmPayment(new Date('2027-06-10T00:00:00Z'));
    const repo = new ListInvoiceRepository([invoice]);
    const service = new MarkOverdueService(repo, notifier, afterDue);

    await service.sweep();

    expect(repo.updated).toHaveLength(0);
    expect(notifier.requests).toHaveLength(0);
  });
});
