import { beforeEach, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import { seedLocations } from '../../../../shared/infrastructure/database/seed.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { CustomsDeclaration } from '../../domain/model/customs-declaration.js';
import { KyselyCustomsDeclarationRepository } from './kysely-customs-declaration-repository.js';

describe('KyselyCustomsDeclarationRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyCustomsDeclarationRepository;
  const bookingId = randomUUID();
  let activityId: number;

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    repo = new KyselyCustomsDeclarationRepository(db);
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

  it('save と findByDeclarationNumber で往復できる', async () => {
    const declaration = CustomsDeclaration.register({
      declarationNumber: 'DECL-RT01',
      handlingActivityId: activityId,
      declaredAt: new Date('2026-09-10T10:00:00Z'),
      remarks: '検査待ち',
    });
    await repo.save(declaration);
    const found = await repo.findByDeclarationNumber('DECL-RT01');
    expect(found).not.toBeNull();
    expect(found!.status).toBe('PENDING');
    expect(found!.remarks).toBe('検査待ち');
    expect(found!.clearedAt).toBeNull();
  });

  it('update で状態と clearedAt を永続化する', async () => {
    const declaration = CustomsDeclaration.register({
      declarationNumber: 'DECL-UP01',
      handlingActivityId: activityId,
      declaredAt: new Date('2026-09-10T10:00:00Z'),
    });
    await repo.save(declaration);
    declaration.clear(new Date('2026-09-12T00:00:00Z'));
    await repo.update(declaration);
    const found = await repo.findByDeclarationNumber('DECL-UP01');
    expect(found!.status).toBe('CLEARED');
    expect(found!.clearedAt).toEqual(new Date('2026-09-12T00:00:00Z'));
  });

  it('存在しない申告番号は null', async () => {
    expect(await repo.findByDeclarationNumber('DECL-NONE')).toBeNull();
  });

  it('findHandlingContext は荷役作業の場所と貨物の追跡番号を返す', async () => {
    const shipper = await db
      .insertInto('shipper')
      .values({ shipperCode: 'SHP-ctx001', shipperType: 'INDIVIDUAL', name: '荷主', email: 's@example.com' })
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
        trackingNumber: 'TRK-CTX',
      })
      .execute();
    const context = await repo.findHandlingContext(activityId);
    expect(context).toEqual({ bookingId, location: 'JPTYO', trackingNumber: 'TRK-CTX' });
  });

  it('追跡番号未発行なら findHandlingContext.trackingNumber は null', async () => {
    const context = await repo.findHandlingContext(activityId);
    expect(context).toEqual({ bookingId, location: 'JPTYO', trackingNumber: null });
  });

  it('存在しない荷役作業は findHandlingContext が null', async () => {
    expect(await repo.findHandlingContext(9999)).toBeNull();
  });
});
