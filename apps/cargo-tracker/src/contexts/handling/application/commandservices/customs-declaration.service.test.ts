import { beforeEach, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import { seedLocations } from '../../../../shared/infrastructure/database/seed.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { HandlingHistoryQueryService } from '../queryservices/handling-history-query.service.js';
import {
  CustomsDeclarationService,
  DeclarationNotFoundError,
  HandlingActivityNotFoundError,
} from './customs-declaration.service.js';

describe('CustomsDeclarationService（US16 前提条件・pg-mem 統合）', () => {
  let db: AppDatabase;
  let service: CustomsDeclarationService;
  let history: HandlingHistoryQueryService;
  const bookingId = randomUUID();
  let activityId: number;

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    service = new CustomsDeclarationService(db);
    history = new HandlingHistoryQueryService(db);
    await seedLocations(db);
    const saved = await db
      .insertInto('handling_activity')
      .values({
        bookingId,
        eventType: 'CUSTOMS',
        eventCompletionTime: new Date('2026-09-10T10:00:00Z'),
        locationUnlocode: 'JPTYO',
        voyageNumber: null,
        operatorName: null,
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    activityId = saved.id;
  });

  it('通関申告を PENDING で登録し、CLEARED へ更新できる', async () => {
    const declarationNumber = await service.register({
      handlingActivityId: activityId,
      declaredAt: new Date('2026-09-10T10:00:00Z'),
    });
    expect(declarationNumber).toMatch(/^DECL-/);
    expect(await history.isCustomsCleared(bookingId)).toBe(false);

    await service.updateStatus(declarationNumber, 'CLEARED');
    expect(await history.isCustomsCleared(bookingId)).toBe(true);
    const row = await db.selectFrom('customs_declaration').selectAll().executeTakeFirstOrThrow();
    expect(row.status).toBe('CLEARED');
    expect(row.clearedAt).not.toBeNull();
  });

  it('HELD / REJECTED への更新では cleared_at は設定されない', async () => {
    const declarationNumber = await service.register({
      handlingActivityId: activityId,
      declaredAt: new Date('2026-09-10T10:00:00Z'),
    });
    await service.updateStatus(declarationNumber, 'HELD');
    const row = await db.selectFrom('customs_declaration').selectAll().executeTakeFirstOrThrow();
    expect(row.status).toBe('HELD');
    expect(row.clearedAt).toBeNull();
    expect(await history.isCustomsCleared(bookingId)).toBe(false);
  });

  it('存在しない荷役作業への申告登録はエラー', async () => {
    await expect(
      service.register({ handlingActivityId: 9999, declaredAt: new Date() }),
    ).rejects.toThrow(HandlingActivityNotFoundError);
  });

  it('存在しない申告番号の更新はエラー', async () => {
    await expect(service.updateStatus('DECL-MISSING', 'CLEARED')).rejects.toThrow(DeclarationNotFoundError);
  });

  it('不正なステータスはエラー', async () => {
    const declarationNumber = await service.register({
      handlingActivityId: activityId,
      declaredAt: new Date(),
    });
    await expect(service.updateStatus(declarationNumber, 'INVALID')).rejects.toThrow();
  });
});
