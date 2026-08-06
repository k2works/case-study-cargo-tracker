import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { RouteCandidate } from '../../domain/model/route-candidate.js';
import type { EstimateRepository } from '../../domain/repository/estimate-repository.js';
import type { RouteCandidateCalculator } from '../outboundservices/route-candidate-calculator.js';
import { CreateEstimateService } from './create-estimate.service.js';

function candidate(days: number): RouteCandidate {
  return RouteCandidate.of({
    voyageNumber: 'V001',
    transitPort: 'SGSIN',
    transitDays: days,
    estimatedCost: 100000,
  });
}

describe('CreateEstimateService', () => {
  let repo: EstimateRepository;
  let calculator: RouteCandidateCalculator;
  let service: CreateEstimateService;

  beforeEach(() => {
    repo = {
      save: vi.fn().mockResolvedValue(1),
      findByEstimateId: vi.fn(),
      findAll: vi.fn(),
    };
    calculator = { calculate: vi.fn() };
    service = new CreateEstimateService(repo, calculator);
  });

  const base = {
    origin: 'JPTYO',
    destination: 'USLAX',
    cargoType: CargoType.GENERAL,
    weightKg: 1000,
    // 十分に先の期限
    arrivalDeadline: new Date(Date.now() + 1000 * 60 * 60 * 24 * 60),
    now: new Date(),
  };

  it('見積を保存しルート候補と見積番号を返す', async () => {
    vi.mocked(calculator.calculate).mockResolvedValue([candidate(14)]);
    const result = await service.create(base);
    expect(result.estimateId).toMatch(/^[0-9a-f-]{36}$/);
    expect(result.candidates).toHaveLength(1);
    expect(result.deadlineMet).toBe(true);
    expect(repo.save).toHaveBeenCalledOnce();
  });

  it('期限に間に合うルートが無い場合 deadlineMet=false を返す', async () => {
    // 到着期限は 3 日後だが所要 30 日
    const soon = new Date(Date.now() + 1000 * 60 * 60 * 24 * 3);
    vi.mocked(calculator.calculate).mockResolvedValue([candidate(30)]);
    const result = await service.create({ ...base, arrivalDeadline: soon });
    expect(result.deadlineMet).toBe(false);
  });

  it('候補が空でも見積は保存され deadlineMet=false', async () => {
    vi.mocked(calculator.calculate).mockResolvedValue([]);
    const result = await service.create(base);
    expect(result.deadlineMet).toBe(false);
    expect(repo.save).toHaveBeenCalledOnce();
  });
});
