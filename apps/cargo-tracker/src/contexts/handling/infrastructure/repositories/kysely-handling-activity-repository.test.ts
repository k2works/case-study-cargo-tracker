import { beforeEach, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import { seedLocations } from '../../../../shared/infrastructure/database/seed.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { HandlingActivity } from '../../domain/model/handling-activity.js';
import { KyselyHandlingActivityRepository } from './kysely-handling-activity-repository.js';
import { HandlingHistoryQueryService } from '../../application/queryservices/handling-history-query.service.js';
import { KyselyCargoSnapshot } from './kysely-cargo-snapshot.js';

describe('Handling インフラ（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyHandlingActivityRepository;
  const bookingId = randomUUID();

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    repo = new KyselyHandlingActivityRepository(db);
    await seedLocations(db);
  });

  it('荷役作業を保存し bookingId で時系列に取得する', async () => {
    await repo.save(
      HandlingActivity.register({
        bookingId,
        type: 'LOAD',
        location: 'JPTYO',
        completionTime: new Date('2026-09-02T10:00:00Z'),
        voyageNumber: 'V001',
        operatorName: '作業員A',
      }),
    );
    const saved = await repo.save(
      HandlingActivity.register({
        bookingId,
        type: 'RECEIVE',
        location: 'JPTYO',
        completionTime: new Date('2026-09-01T10:00:00Z'),
      }),
    );
    expect(saved.id).not.toBeNull();

    const found = await repo.findByBookingId(bookingId);
    expect(found).toHaveLength(2);
    expect(found[0].type.value).toBe('RECEIVE'); // 時系列昇順
    expect(found[1].voyageNumber?.value).toBe('V001');
  });

  it('CLAIM は荷受人確認コードを永続化し、非 CLAIM では null になる', async () => {
    await repo.save(
      HandlingActivity.register({
        bookingId,
        type: 'CLAIM',
        location: 'USLAX',
        completionTime: new Date('2026-09-20T10:00:00Z'),
        consigneeConfirmation: 'SIGN-9876',
      }),
    );
    await repo.save(
      HandlingActivity.register({
        bookingId,
        type: 'RECEIVE',
        location: 'JPTYO',
        completionTime: new Date('2026-09-01T10:00:00Z'),
        // 非 CLAIM に確認コードを渡しても保持しない
        consigneeConfirmation: '無視される',
      }),
    );

    const rows = await db
      .selectFrom('handling_activity')
      .select(['eventType', 'consigneeConfirmation'])
      .where('bookingId', '=', bookingId)
      .orderBy('eventType', 'asc')
      .execute();
    const byType = Object.fromEntries(rows.map((r) => [r.eventType, r.consigneeConfirmation]));
    expect(byType.CLAIM).toBe('SIGN-9876');
    expect(byType.RECEIVE).toBeNull();

    const found = await repo.findByBookingId(bookingId);
    const claim = found.find((a) => a.type.value === 'CLAIM');
    expect(claim?.consigneeConfirmation).toBe('SIGN-9876');
  });

  it('荷役履歴 Read Model が最新イベントと通関状態を返す', async () => {
    const query = new HandlingHistoryQueryService(db);
    const receive = await repo.save(
      HandlingActivity.register({
        bookingId,
        type: 'RECEIVE',
        location: 'JPTYO',
        completionTime: new Date('2026-09-01T10:00:00Z'),
      }),
    );
    await repo.save(
      HandlingActivity.register({
        bookingId,
        type: 'LOAD',
        location: 'JPTYO',
        completionTime: new Date('2026-09-02T10:00:00Z'),
        voyageNumber: 'V001',
      }),
    );

    const latest = await query.mostRecentlyCompletedEvent(bookingId);
    expect(latest?.eventType).toBe('LOAD');

    // 通関 CLEARED 前は false、CLEARED 登録後は true
    expect(await query.isCustomsCleared(bookingId)).toBe(false);
    await db
      .insertInto('customs_declaration')
      .values({
        handlingActivityId: receive.id!,
        declarationNumber: 'DECL-001',
        declaredAt: new Date('2026-09-03T00:00:00Z'),
        status: 'CLEARED',
        clearedAt: new Date('2026-09-04T00:00:00Z'),
      })
      .execute();
    expect(await query.isCustomsCleared(bookingId)).toBe(true);
  });

  it('CargoSnapshotAcl が追跡番号から旅程付きスナップショットを返す', async () => {
    const shipper = await db
      .insertInto('shipper')
      .values({ shipperCode: 'SHP-1', shipperType: 'INDIVIDUAL', name: '荷主', email: 's@example.com' })
      .returning('id')
      .executeTakeFirstOrThrow();
    const cargo = await db
      .insertInto('cargo')
      .values({
        bookingId,
        shipperId: shipper.id,
        cargoType: 'GENERAL',
        weight: '1200',
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'USLAX',
        arrivalDeadline: new Date('2026-09-30'),
        bookingStatus: 'TRACKING_ISSUED',
        trackingNumber: 'TRK-TEST-0001',
        routingStatus: 'ROUTED',
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    await db
      .insertInto('leg')
      .values({
        cargoId: cargo.id,
        voyageNumber: 'V001',
        loadLocationUnlocode: 'JPTYO',
        unloadLocationUnlocode: 'USLAX',
        loadTime: new Date('2026-09-01T00:00:00Z'),
        unloadTime: new Date('2026-09-15T00:00:00Z'),
        seqNumber: 1,
      })
      .execute();

    const acl = new KyselyCargoSnapshot(db);
    const snapshot = await acl.findByTrackingNumber('TRK-TEST-0001');
    expect(snapshot).not.toBeNull();
    expect(snapshot!.bookingId).toBe(bookingId);
    expect(snapshot!.origin).toBe('JPTYO');
    expect(snapshot!.routingStatus).toBe('ROUTED');
    expect(snapshot!.itineraryLegs).toEqual([
      { loadLocation: 'JPTYO', unloadLocation: 'USLAX', voyageNumber: 'V001' },
    ]);

    expect(await acl.findByTrackingNumber('TRK-MISSING')).toBeNull();
  });
});
