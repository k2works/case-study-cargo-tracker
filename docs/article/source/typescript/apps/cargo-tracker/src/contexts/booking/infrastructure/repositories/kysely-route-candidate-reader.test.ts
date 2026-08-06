import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import { seedLocations } from '../../../../shared/infrastructure/database/seed.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { KyselyRouteCandidateReader } from './kysely-route-candidate-reader.js';

async function seedVoyage(
  db: AppDatabase,
  voyageNumber: string,
  movements: { departure: string; arrival: string; departureDate: string; arrivalDate: string; seq: number }[],
): Promise<void> {
  const voyage = await db
    .insertInto('voyage')
    .values({ voyageNumber, shipName: 'Ship', carrierName: 'Carrier', supportedCargoTypes: 'GENERAL' })
    .returning('id')
    .executeTakeFirstOrThrow();
  for (const m of movements) {
    await db
      .insertInto('carrier_movement')
      .values({
        voyageId: voyage.id,
        departureLocationUnlocode: m.departure,
        arrivalLocationUnlocode: m.arrival,
        departureDate: m.departureDate,
        arrivalDate: m.arrivalDate,
        seqNumber: m.seq,
      })
      .execute();
  }
}

describe('KyselyRouteCandidateReader（pg-mem 統合）', () => {
  let db: AppDatabase;
  let reader: KyselyRouteCandidateReader;

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    await seedLocations(db);
    reader = new KyselyRouteCandidateReader(db);
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('直行便を Leg ドラフト付きで返す', async () => {
    await seedVoyage(db, 'V001', [
      { departure: 'JPTYO', arrival: 'USLAX', departureDate: '2026-09-01T00:00:00Z', arrivalDate: '2026-09-15T00:00:00Z', seq: 1 },
    ]);
    const candidates = await reader.findCandidates({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: new Date('2026-09-30'),
      cargoType: 'GENERAL',
    });
    expect(candidates).toHaveLength(1);
    expect(candidates[0].legs).toHaveLength(1);
    expect(candidates[0].legs[0].voyageNumber).toBe('V001');
    expect(candidates[0].transitPorts).toEqual([]);
  });

  it('1 寄港接続の候補を返し、直行を優先順位で上位にする', async () => {
    await seedVoyage(db, 'V001', [
      { departure: 'JPTYO', arrival: 'USLAX', departureDate: '2026-09-01T00:00:00Z', arrivalDate: '2026-09-15T00:00:00Z', seq: 1 },
    ]);
    await seedVoyage(db, 'V002', [
      { departure: 'JPTYO', arrival: 'HKHKG', departureDate: '2026-09-01T00:00:00Z', arrivalDate: '2026-09-04T00:00:00Z', seq: 1 },
    ]);
    await seedVoyage(db, 'V003', [
      { departure: 'HKHKG', arrival: 'USLAX', departureDate: '2026-09-05T00:00:00Z', arrivalDate: '2026-09-20T00:00:00Z', seq: 1 },
    ]);
    const candidates = await reader.findCandidates({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: new Date('2026-09-30'),
      cargoType: 'GENERAL',
    });
    expect(candidates.length).toBeGreaterThanOrEqual(2);
    expect(candidates[0].voyageNumbers).toHaveLength(1);
    const transit = candidates.find((c) => c.voyageNumbers.length === 2);
    expect(transit?.transitPorts).toEqual(['HKHKG']);
    expect(transit?.legs).toHaveLength(2);
  });

  it('期限当日に時刻付きで到着する候補は期限内として採用する（日付単位比較の境界）', async () => {
    await seedVoyage(db, 'V001', [
      { departure: 'JPTYO', arrival: 'USLAX', departureDate: '2026-09-01T00:00:00Z', arrivalDate: '2026-09-30T20:00:00Z', seq: 1 },
    ]);
    const candidates = await reader.findCandidates({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: new Date('2026-09-30'),
      cargoType: 'GENERAL',
    });
    expect(candidates).toHaveLength(1);
  });

  it('期限翌日に到着する候補は除外する（境界+1）', async () => {
    await seedVoyage(db, 'V001', [
      { departure: 'JPTYO', arrival: 'USLAX', departureDate: '2026-09-01T00:00:00Z', arrivalDate: '2026-10-01T00:00:00Z', seq: 1 },
    ]);
    const candidates = await reader.findCandidates({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: new Date('2026-09-30'),
      cargoType: 'GENERAL',
    });
    expect(candidates).toHaveLength(0);
  });

  it('期限を過ぎる候補は除外する', async () => {
    await seedVoyage(db, 'V001', [
      { departure: 'JPTYO', arrival: 'USLAX', departureDate: '2026-09-01T00:00:00Z', arrivalDate: '2026-10-15T00:00:00Z', seq: 1 },
    ]);
    const candidates = await reader.findCandidates({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: new Date('2026-09-30'),
      cargoType: 'GENERAL',
    });
    expect(candidates).toHaveLength(0);
  });
});
