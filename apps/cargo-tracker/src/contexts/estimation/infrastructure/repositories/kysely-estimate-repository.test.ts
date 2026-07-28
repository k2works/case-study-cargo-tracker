import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { Estimate } from '../../domain/model/estimate.js';
import { RouteCandidate } from '../../domain/model/route-candidate.js';
import { KyselyEstimateRepository } from './kysely-estimate-repository.js';

function makeEstimate(): Estimate {
  const e = Estimate.create({
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: new Date('2026-09-30'),
    cargoType: CargoType.GENERAL,
    weightKg: 1500,
  });
  e.replaceCandidates([
    RouteCandidate.of({ voyageNumber: 'V001', transitPort: 'SGSIN', transitDays: 14, estimatedCost: 150000 }),
    RouteCandidate.of({ voyageNumber: 'V002', transitPort: null, transitDays: 21, estimatedCost: 127500 }),
  ]);
  return e;
}

describe('KyselyEstimateRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyEstimateRepository;

  beforeEach(() => {
    db = createPgMemDatabase().db;
    repo = new KyselyEstimateRepository(db);
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('見積とルート候補を保存し estimateId で取得する', async () => {
    const estimate = makeEstimate();
    await repo.save(estimate);

    const found = await repo.findByEstimateId(estimate.estimateId.value);
    expect(found).not.toBeNull();
    expect(found?.origin.unlocode).toBe('JPTYO');
    expect(found?.candidates).toHaveLength(2);
    expect(found?.candidates[0].voyageNumber).toBe('V001');
    expect(found?.weightKg).toBe(1500);
  });

  it('findAll で保存済み見積を列挙する', async () => {
    await repo.save(makeEstimate());
    await repo.save(makeEstimate());
    const all = await repo.findAll();
    expect(all.length).toBe(2);
  });

  it('未登録 estimateId は null を返す', async () => {
    expect(await repo.findByEstimateId('00000000-0000-0000-0000-000000000000')).toBeNull();
  });
});
