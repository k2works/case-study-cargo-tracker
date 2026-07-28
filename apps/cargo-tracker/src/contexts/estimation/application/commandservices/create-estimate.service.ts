import type { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Estimate } from '../../domain/model/estimate.js';
import type { RouteCandidate } from '../../domain/model/route-candidate.js';
import type { EstimateRepository } from '../../domain/repository/estimate-repository.js';
import type { RouteCandidateCalculator } from '../outboundservices/route-candidate-calculator.js';

export interface CreateEstimateCommand {
  origin: string;
  destination: string;
  arrivalDeadline: Date;
  cargoType: CargoType;
  weightKg: number;
  /** 期限判定の基準時刻（テスト容易性のため注入可能。省略時は現在時刻） */
  now?: Date;
}

export interface CreateEstimateResult {
  estimateId: string;
  candidates: readonly RouteCandidate[];
  /** 希望期限に間に合うルート候補が存在するか（US01 受入基準） */
  deadlineMet: boolean;
}

/**
 * 見積作成ユースケース（US01）。
 * ルート候補をスタブ算出し、希望期限に間に合う候補があるか判定する。
 */
export class CreateEstimateService {
  constructor(
    private readonly estimates: EstimateRepository,
    private readonly calculator: RouteCandidateCalculator,
  ) {}

  async create(command: CreateEstimateCommand): Promise<CreateEstimateResult> {
    const estimate = Estimate.create({
      origin: command.origin,
      destination: command.destination,
      arrivalDeadline: command.arrivalDeadline,
      cargoType: command.cargoType,
      weightKg: command.weightKg,
    });

    const candidates = await this.calculator.calculate({
      origin: command.origin,
      destination: command.destination,
      cargoType: command.cargoType,
      weightKg: command.weightKg,
    });
    estimate.replaceCandidates(candidates);

    await this.estimates.save(estimate);

    return {
      estimateId: estimate.estimateId.value,
      candidates,
      deadlineMet: estimate.isDeadlineMet(command.now ?? new Date()),
    };
  }
}
