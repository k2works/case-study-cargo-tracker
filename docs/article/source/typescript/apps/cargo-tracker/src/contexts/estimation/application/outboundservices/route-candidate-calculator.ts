import type { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import type { RouteCandidate } from '../../domain/model/route-candidate.js';

export interface RouteQuery {
  origin: string;
  destination: string;
  cargoType: CargoType;
  weightKg: number;
}

/**
 * ルート候補算出ポート（外部経路システムへの ACL）。
 * 現在はスタブ実装。将来 ExternalRoutingServicePort に置換する（ADR 予定）。
 */
export interface RouteCandidateCalculator {
  calculate(query: RouteQuery): Promise<RouteCandidate[]>;
}
