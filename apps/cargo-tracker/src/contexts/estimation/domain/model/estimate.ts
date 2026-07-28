import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Location } from '../../../../shared/domain/model/location.js';
import { EstimateId } from './estimate-id.js';
import { EstimateStatus } from './estimate-status.js';
import { RouteCandidate } from './route-candidate.js';

interface CreateEstimateParams {
  origin: string;
  destination: string;
  arrivalDeadline: Date;
  cargoType: CargoType;
  weightKg: number;
}

interface ReconstructParams {
  id: number;
  estimateId: string;
  origin: string;
  destination: string;
  arrivalDeadline: Date;
  cargoType: CargoType;
  weightKg: number;
  status: EstimateStatus;
  candidates: RouteCandidate[];
}

/**
 * 見積集約ルート。輸送見積の中心。出発地・仕向地・期限・貨物種別・重量とルート候補を管理する。
 */
export class Estimate {
  private _candidates: RouteCandidate[];

  private constructor(
    readonly id: number | undefined,
    readonly estimateId: EstimateId,
    readonly origin: Location,
    readonly destination: Location,
    readonly arrivalDeadline: Date,
    readonly cargoType: CargoType,
    readonly weightKg: number,
    readonly status: EstimateStatus,
    candidates: RouteCandidate[],
  ) {
    this._candidates = candidates;
  }

  get candidates(): readonly RouteCandidate[] {
    return this._candidates;
  }

  static create(params: CreateEstimateParams): Estimate {
    const origin = Location.of(params.origin);
    const destination = Location.of(params.destination);
    if (origin.sameAs(destination)) {
      throw new Error('出発地と目的地は異なる必要があります');
    }
    if (params.weightKg <= 0) {
      throw new Error('重量は正の値が必要です');
    }
    return new Estimate(
      undefined,
      EstimateId.generate(),
      origin,
      destination,
      params.arrivalDeadline,
      params.cargoType,
      params.weightKg,
      EstimateStatus.CREATED,
      [],
    );
  }

  static reconstruct(params: ReconstructParams): Estimate {
    return new Estimate(
      params.id,
      EstimateId.of(params.estimateId),
      Location.of(params.origin),
      Location.of(params.destination),
      params.arrivalDeadline,
      params.cargoType,
      params.weightKg,
      params.status,
      params.candidates,
    );
  }

  /** ルート候補を一括入れ替えする */
  replaceCandidates(candidates: RouteCandidate[]): void {
    this._candidates = candidates;
  }
}
