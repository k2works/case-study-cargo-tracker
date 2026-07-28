import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { RouteCandidate } from '../../domain/model/route-candidate.js';
import type {
  RouteCandidateCalculator,
  RouteQuery,
} from '../../application/outboundservices/route-candidate-calculator.js';

/** 貨物種別ごとの料金係数（domain-model 料金計算ロジックに準拠） */
const CARGO_TYPE_FACTOR: Record<CargoType, number> = {
  GENERAL: 1.0,
  HAZARDOUS: 1.8,
  REFRIGERATED: 1.5,
};

/** 距離係数（スタブの固定値）。将来は経路距離から算出 */
const BASE_RATE_PER_KG = 100;

/**
 * ルート候補算出のスタブ実装。
 * 重量・貨物種別から概算料金を固定計算し、直行/経由の 2 候補を返す。
 * 将来、外部ルーティングサービス連携時にアダプタを差し替える。
 */
export class StubRouteCandidateCalculator implements RouteCandidateCalculator {
  async calculate(query: RouteQuery): Promise<RouteCandidate[]> {
    const factor = CARGO_TYPE_FACTOR[query.cargoType] ?? 1.0;
    const baseCost = Math.round(query.weightKg * BASE_RATE_PER_KG * factor);

    return [
      RouteCandidate.of({
        voyageNumber: `V-${query.origin}-${query.destination}-D`,
        transitPort: null,
        transitDays: 14,
        estimatedCost: baseCost,
      }),
      RouteCandidate.of({
        voyageNumber: `V-${query.origin}-${query.destination}-T`,
        transitPort: 'SGSIN',
        transitDays: 21,
        estimatedCost: Math.round(baseCost * 0.85),
      }),
    ];
  }
}
