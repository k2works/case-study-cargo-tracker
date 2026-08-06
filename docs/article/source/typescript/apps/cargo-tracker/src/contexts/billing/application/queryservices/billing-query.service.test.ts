import { beforeEach, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { BillingQueryService } from './billing-query.service.js';
import { PaymentStatus } from '../../domain/model/payment-status.js';

describe('BillingQueryService.countOverdue（動的な期限超過件数）', () => {
  let db: AppDatabase;

  beforeEach(() => {
    db = createPgMemDatabase().db;
  });

  /** PENDING 請求書を支払期限（dueDate）指定で直接投入する（読みモデルの検証用シード） */
  async function seedPending(dueDate: Date): Promise<void> {
    await db
      .insertInto('invoice')
      .values({
        invoiceNumber: `INV-${randomUUID().slice(0, 8)}`,
        bookingId: randomUUID(),
        shipperId: 'SHP-1',
        shipperType: 'INDIVIDUAL',
        baseAmountValue: 1000,
        baseAmountCurrency: 'JPY',
        totalAmountValue: 1100,
        totalAmountCurrency: 'JPY',
        taxRate: '0.1000',
        taxAmount: '100',
        discountRate: '0',
        paymentStatus: PaymentStatus.PENDING,
        dueDate,
        discountAmountValue: 0,
        discountAmountCurrency: 'JPY',
      })
      .execute();
  }

  it('sweep 未実行でも PENDING かつ支払期限超過の請求を件数に含める（architect#3/user#3）', async () => {
    await seedPending(new Date('2026-01-31T00:00:00Z')); // now より前 = 超過
    await seedPending(new Date('2026-06-30T00:00:00Z')); // now より後 = 期限内

    const query = new BillingQueryService(db, () => new Date('2026-03-01T00:00:00Z'));
    expect(await query.countOverdue()).toBe(1);
  });

  it('全て期限内なら 0 件', async () => {
    await seedPending(new Date('2026-06-30T00:00:00Z'));
    const query = new BillingQueryService(db, () => new Date('2026-06-10T00:00:00Z'));
    expect(await query.countOverdue()).toBe(0);
  });
});
