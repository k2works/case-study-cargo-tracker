import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { CarrierMovement, Schedule, Voyage } from '../../domain/model/voyage.js';
import { KyselyVoyageRepository } from './kysely-voyage-repository.js';

async function seedLocations(db: AppDatabase): Promise<void> {
  await db
    .insertInto('location')
    .values([
      { unlocode: 'JPTYO', name: 'Tokyo' },
      { unlocode: 'HKHKG', name: 'Hong Kong' },
      { unlocode: 'SGSIN', name: 'Singapore' },
    ])
    .execute();
}

function makeVoyage(voyageNumber = 'V001'): Voyage {
  return Voyage.register({
    voyageNumber,
    shipName: 'Pacific Star',
    carrierName: 'Oceanic',
    supportedCargoTypes: [CargoType.GENERAL, CargoType.REFRIGERATED],
    schedule: Schedule.of([
      CarrierMovement.of({
        departureLocation: 'JPTYO',
        arrivalLocation: 'HKHKG',
        departureTime: new Date('2026-09-01T09:00:00Z'),
        arrivalTime: new Date('2026-09-04T10:00:00Z'),
      }),
      CarrierMovement.of({
        departureLocation: 'HKHKG',
        arrivalLocation: 'SGSIN',
        departureTime: new Date('2026-09-05T12:00:00Z'),
        arrivalTime: new Date('2026-09-08T08:00:00Z'),
      }),
    ]),
  });
}

describe('KyselyVoyageRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyVoyageRepository;

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    repo = new KyselyVoyageRepository(db);
    await seedLocations(db);
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('航海と運送区間を保存し voyageNumber で復元する', async () => {
    const voyage = makeVoyage();
    await repo.save(voyage);

    const found = await repo.findByVoyageNumber('V001');
    expect(found).not.toBeNull();
    expect(found?.shipName).toBe('Pacific Star');
    expect(found?.carrierName).toBe('Oceanic');
    expect(found?.supports(CargoType.REFRIGERATED)).toBe(true);
    expect(found?.schedule.carrierMovements).toHaveLength(2);
    expect(found?.arrivalTime('SGSIN')).toEqual(new Date('2026-09-08T08:00:00Z'));
  });

  it('同一 voyageNumber の重複登録を DB 制約で拒否する', async () => {
    await repo.save(makeVoyage('V001'));
    await expect(repo.save(makeVoyage('V001'))).rejects.toThrow();
  });

  it('update で carrier_movement を入れ替えて航海スケジュールを更新する', async () => {
    const voyage = makeVoyage();
    await repo.save(voyage);
    const updated = voyage.changeSchedule(
      Schedule.of([
        CarrierMovement.of({
          departureLocation: 'JPTYO',
          arrivalLocation: 'SGSIN',
          departureTime: new Date('2026-09-02T09:00:00Z'),
          arrivalTime: new Date('2026-09-09T10:00:00Z'),
        }),
      ]),
    );

    await repo.update(updated);

    const found = await repo.findByVoyageNumber('V001');
    expect(found?.schedule.carrierMovements).toHaveLength(1);
    expect(found?.arrivalTime('SGSIN')).toEqual(new Date('2026-09-09T10:00:00Z'));
  });

  it('未登録 voyageNumber は null を返す', async () => {
    expect(await repo.findByVoyageNumber('UNKNOWN')).toBeNull();
  });
});
