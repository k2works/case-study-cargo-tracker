import { describe, expect, it, beforeEach } from 'vitest';
import { GenerateInvoiceService } from './generate-invoice.service.js';
import type { InvoiceRepository } from '../../domain/repository/invoice-repository.js';
import type { BillingSnapshotAcl } from '../outboundservices/acl/billing-snapshot-acl.js';
import type {
  BillingNotificationPort,
  BillingNotificationRequest,
} from '../outboundservices/acl/billing-notification-port.js';
import type { BillingSnapshot } from '../../domain/model/billing-snapshot.js';
import { Invoice } from '../../domain/model/invoice.js';
import { BillingValidationError } from '../../domain/model/billing-validation-error.js';

function snapshot(overrides: Partial<BillingSnapshot> = {}): BillingSnapshot {
  return {
    bookingId: 'booking-1',
    shipperId: 'SHP-1',
    shipperName: '荷主一郎',
    shipperEmail: 'shipper@example.com',
    shipperType: 'INDIVIDUAL',
    discountRate: 0,
    weightKg: 1000,
    cargoType: 'GENERAL',
    transitDays: 10,
    origin: 'JPTYO',
    destination: 'USLAX',
    bookingStatus: 'DELIVERED',
    activeExceptionTypes: [],
    resolvedExceptionTypes: [],
    ...overrides,
  };
}

class InMemoryInvoiceRepository implements InvoiceRepository {
  saved: Invoice[] = [];
  constructor(private readonly existingBooking: string | null = null) {}
  async save(invoice: Invoice): Promise<void> {
    this.saved.push(invoice);
  }
  async update(): Promise<void> {}
  async findByInvoiceId(): Promise<Invoice | null> {
    return null;
  }
  async findByInvoiceNumber(): Promise<Invoice | null> {
    return null;
  }
  async findByBookingId(bookingId: string): Promise<Invoice | null> {
    if (this.existingBooking !== null && this.existingBooking === bookingId) {
      // 既存請求を模す（二重請求チェック用）。中身は評価に使わないため個人・割引なしで発行する。
      const { BillingBookingId } = await import('../../domain/model/billing-booking-id.js');
      const { BillingShipperId } = await import('../../domain/model/billing-shipper-id.js');
      const { Money } = await import('../../domain/model/money.js');
      const { DiscountRate } = await import('../../domain/model/discount-rate.js');
      return Invoice.issue({
        invoiceNumber: 'INV-EXIST',
        cargoBookingId: BillingBookingId.of(bookingId),
        shipperId: BillingShipperId.of('SHP-1', 'INDIVIDUAL'),
        baseAmount: Money.of(1000, 'JPY'),
        discountRate: DiscountRate.zero(),
        issuedAt: new Date('2027-01-01T00:00:00Z'),
      });
    }
    return null;
  }
  async findAll(): Promise<Invoice[]> {
    return this.saved;
  }
}

class FakeSnapshotAcl implements BillingSnapshotAcl {
  constructor(private readonly value: BillingSnapshot | null) {}
  async findByBookingId(): Promise<BillingSnapshot | null> {
    return this.value;
  }
}

class RecordingNotifier implements BillingNotificationPort {
  requests: BillingNotificationRequest[] = [];
  async notify(request: BillingNotificationRequest): Promise<void> {
    this.requests.push(request);
  }
}

class FailingNotifier implements BillingNotificationPort {
  async notify(): Promise<void> {
    throw new Error('通知基盤エラー');
  }
}

const fixedClock = () => new Date('2027-06-01T00:00:00Z');

describe('GenerateInvoiceService (US21/US22)', () => {
  let notifier: RecordingNotifier;
  beforeEach(() => {
    notifier = new RecordingNotifier();
  });

  it('DELIVERED 以外の予約は請求できない', async () => {
    const service = new GenerateInvoiceService(
      new InMemoryInvoiceRepository(),
      new FakeSnapshotAcl(snapshot({ bookingStatus: 'IN_TRANSIT' })),
      notifier,
      fixedClock,
    );
    await expect(service.generate('booking-1')).rejects.toBeInstanceOf(BillingValidationError);
  });

  it('既に請求済みの予約は二重請求で拒否される', async () => {
    const service = new GenerateInvoiceService(
      new InMemoryInvoiceRepository('booking-1'),
      new FakeSnapshotAcl(snapshot()),
      notifier,
      fixedClock,
    );
    await expect(service.generate('booking-1')).rejects.toBeInstanceOf(BillingValidationError);
  });

  it('加算・控除調整を明細へ反映して発行する', async () => {
    const repo = new InMemoryInvoiceRepository();
    const service = new GenerateInvoiceService(repo, new FakeSnapshotAcl(snapshot()), notifier, fixedClock);
    await service.generate('booking-1', [
      { description: '補償費用', amountValue: 5000, isDeduction: false },
      { description: '破損減額', amountValue: 3000, isDeduction: true },
    ]);
    expect(repo.saved).toHaveLength(1);
    const descriptions = repo.saved[0].lineItems.map((i) => i.description);
    expect(descriptions.some((d) => d.includes('補償費用'))).toBe(true);
    expect(descriptions.some((d) => d.includes('破損減額'))).toBe(true);
  });

  it('荷主メールが未解決のときは通知をスキップして発行を成立させる', async () => {
    const repo = new InMemoryInvoiceRepository();
    const service = new GenerateInvoiceService(
      repo,
      new FakeSnapshotAcl(snapshot({ shipperEmail: null })),
      notifier,
      fixedClock,
    );
    const invoiceNumber = await service.generate('booking-1');
    expect(invoiceNumber).toMatch(/^INV-/);
    expect(repo.saved).toHaveLength(1);
    expect(notifier.requests).toHaveLength(0);
  });

  it('精算書通知の本文に支払方法・振込先の案内が含まれる（user#1）', async () => {
    const repo = new InMemoryInvoiceRepository();
    const service = new GenerateInvoiceService(repo, new FakeSnapshotAcl(snapshot()), notifier, fixedClock);
    await service.generate('booking-1');
    expect(notifier.requests).toHaveLength(1);
    expect(notifier.requests[0].body).toContain('銀行振込');
    expect(notifier.requests[0].body).toContain('振込先');
  });

  it('通知が失敗しても握りつぶして invoiceNumber を返す', async () => {
    const repo = new InMemoryInvoiceRepository();
    const service = new GenerateInvoiceService(
      repo,
      new FakeSnapshotAcl(snapshot()),
      new FailingNotifier(),
      fixedClock,
    );
    const invoiceNumber = await service.generate('booking-1');
    expect(invoiceNumber).toMatch(/^INV-/);
    expect(repo.saved).toHaveLength(1);
  });
});
