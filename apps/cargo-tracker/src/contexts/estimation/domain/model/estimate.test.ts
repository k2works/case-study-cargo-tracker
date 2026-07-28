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

  describe('isDeadlineMet（日付単位で期限充足を判定）', () => {
    const now = new Date('2026-09-01T15:00:00Z'); // 時刻あり

    function withCandidate(days: number) {
      const e = Estimate.create({ ...validParams(), arrivalDeadline: new Date('2026-09-15') });
      e.replaceCandidates([
        RouteCandidate.of({ voyageNumber: 'V', transitPort: null, transitDays: days, estimatedCost: 1 }),
      ]);
      return e;
    }

    it('到着が期限当日（時刻付き ETA）でも間に合うと判定する', () => {
      // now 09-01 15:00 + 14 日 = 09-15 15:00。期限は 09-15（日付単位）→ met
      expect(withCandidate(14).isDeadlineMet(now)).toBe(true);
    });

    it('期限翌日到着は間に合わない', () => {
      expect(withCandidate(15).isDeadlineMet(now)).toBe(false);
    });

    it('候補が無ければ false', () => {
      const e = Estimate.create({ ...validParams(), arrivalDeadline: new Date('2026-09-15') });
      expect(e.isDeadlineMet(now)).toBe(false);
    });
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
