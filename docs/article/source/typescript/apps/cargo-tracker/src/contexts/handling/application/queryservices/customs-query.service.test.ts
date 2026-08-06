import { beforeEach, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import { seedLocations } from '../../../../shared/infrastructure/database/seed.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { CustomsQueryService } from './customs-query.service.js';

describe('CustomsQueryService（通関状態 Read Model・pg-mem 統合）', () => {
  let db: AppDatabase;
  let query: CustomsQueryService;
  const bookingId = randomUUID();
  const trackingNumber = 'TRK-CUSTOMS01';

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    query = new CustomsQueryService(db);
    await seedLocations(db);
    const shipper = await db
      .insertInto('shipper')
      .values({ shipperCode: 'SHP-abc12345', shipperType: 'INDIVIDUAL', name: '荷主', email: 's@example.com' })
      .returning('id')
      .executeTakeFirstOrThrow();
    await db
      .insertInto('cargo')
      .values({
        bookingId,
        shipperId: shipper.id,
        cargoType: 'GENERAL',
        weight: 1000,
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'USLAX',
        arrivalDeadline: new Date('2026-09-30T00:00:00Z'),
        trackingNumber,
      })
      .execute();
  });

  async function seedActivity(eventType: string, at: string): Promise<number> {
    const saved = await db
      .insertInto('handling_activity')
      .values({
        bookingId,
        eventType,
        eventCompletionTime: new Date(at),
        locationUnlocode: 'JPTYO',
        voyageNumber: null,
        operatorName: null,
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    return saved.id;
  }

  it('追跡番号が見つからなければ null', async () => {
    expect(await query.findByTrackingNumber('TRK-UNKNOWN')).toBeNull();
  });

  it('申告がなければ空配列で最新荷役作業 ID を返す', async () => {
    await seedActivity('RECEIVE', '2026-09-01T08:00:00Z');
    const latest = await seedActivity('CUSTOMS', '2026-09-10T10:00:00Z');
    const result = await query.findByTrackingNumber(trackingNumber);
    expect(result).not.toBeNull();
    expect(result!.bookingId).toBe(bookingId);
    expect(result!.declarations).toHaveLength(0);
    expect(result!.latestHandlingActivityId).toBe(latest);
  });

  it('申告を新しい順に返す', async () => {
    const activityId = await seedActivity('CUSTOMS', '2026-09-10T10:00:00Z');
    await db
      .insertInto('customs_declaration')
      .values([
        { handlingActivityId: activityId, declarationNumber: 'DECL-A', declaredAt: new Date('2026-09-10T10:00:00Z'), status: 'PENDING' },
        { handlingActivityId: activityId, declarationNumber: 'DECL-B', declaredAt: new Date('2026-09-11T10:00:00Z'), status: 'HELD' },
      ])
      .execute();
    const result = await query.findByTrackingNumber(trackingNumber);
    expect(result!.declarations.map((d) => d.declarationNumber)).toEqual(['DECL-B', 'DECL-A']);
    expect(result!.declarations[0].status).toBe('HELD');
  });
});
