import { beforeEach, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import { seedLocations } from '../../../../shared/infrastructure/database/seed.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { TrackingActivity } from '../../domain/model/tracking-activity.js';
import { TrackingStatus } from '../../domain/model/tracking-status.js';
import { KyselyTrackingActivityRepository } from './kysely-tracking-activity-repository.js';

describe('KyselyTrackingActivityRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyTrackingActivityRepository;
  const bookingId = randomUUID();

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    repo = new KyselyTrackingActivityRepository(db);
    await seedLocations(db);
  });

  it('NOT_RECEIVED の追跡レコードを作成し追跡番号で復元する', async () => {
    await repo.save(TrackingActivity.create('TRK-0001', bookingId));
    const found = await repo.findByTrackingNumber('TRK-0001');
    expect(found).not.toBeNull();
    expect(found!.bookingId).toBe(bookingId);
    expect(found!.currentStatus()).toBe(TrackingStatus.NOT_RECEIVED);

    const row = await db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(row.transportStatus).toBe('NOT_RECEIVED');
  });

  it('イベント追加を差分永続化し transport_status を同期する', async () => {
    const saved = await repo.save(TrackingActivity.create('TRK-0001', bookingId));
    saved.addEvent({ eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('2026-09-01T10:00:00Z'), voyageNumber: null });
    await repo.save(saved);

    // 同じ集約を再度 save しても重複イベントは増えない（冪等）
    const reloaded = await repo.findByTrackingNumber('TRK-0001');
    reloaded!.addEvent({ eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('2026-09-01T10:00:00Z'), voyageNumber: null });
    await repo.save(reloaded!);

    const events = await db.selectFrom('tracking_handling_event').selectAll().execute();
    expect(events).toHaveLength(1);
    const row = await db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(row.transportStatus).toBe('RECEIVED');
  });

  it('未登録の追跡番号は null', async () => {
    expect(await repo.findByTrackingNumber('TRK-MISSING')).toBeNull();
  });
});
