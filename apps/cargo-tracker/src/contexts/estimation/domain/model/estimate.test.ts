import { describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Estimate } from './estimate.js';
import { RouteCandidate } from './route-candidate.js';
import { EstimateStatus } from './estimate-status.js';

function validParams() {
  return {
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: new Date('2026-09-30'),
    cargoType: CargoType.GENERAL,
    weightKg: 1000,
  };
}

describe('Estimate 集約', () => {
  it('見積を作成できる（EstimateId 自動発行・CREATED）', () => {
    const estimate = Estimate.create(validParams());
    expect(estimate.estimateId.value).toMatch(/^[0-9a-f-]{36}$/);
    expect(estimate.status).toBe(EstimateStatus.CREATED);
    expect(estimate.candidates).toEqual([]);
  });

  it('出発地と目的地が同一なら作成できない', () => {
    expect(() => Estimate.create({ ...validParams(), destination: 'JPTYO' })).toThrow();
  });

  it('重量が 0 以下なら作成できない', () => {
    expect(() => Estimate.create({ ...validParams(), weightKg: 0 })).toThrow();
  });

  it('ルート候補を差し替えできる', () => {
    const estimate = Estimate.create(validParams());
    const candidate = RouteCandidate.of({
      voyageNumber: 'V001',
      transitPort: 'SGSIN',
      transitDays: 14,
      estimatedCost: 120000,
    });
    estimate.replaceCandidates([candidate]);
    expect(estimate.candidates).toHaveLength(1);
    expect(estimate.candidates[0].voyageNumber).toBe('V001');
  });
});
