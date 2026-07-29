import type { LegDraft } from '../../commandservices/route-cargo.service.js';

export interface RouteCandidateQuery {
  origin: string;
  destination: string;
  arrivalDeadline: Date;
  cargoType: string;
}

/** 経路候補の選択肢。id は選択時に POST され、legs から CargoItinerary を組み立てる */
export interface RouteCandidateOption {
  id: string;
  voyageNumbers: string[];
  transitPorts: string[];
  transitDays: number;
  estimatedCost: number;
  legs: LegDraft[];
}

/**
 * 経路候補 ACL（Booking → Routing）。
 * Routing Context の航海スケジュールを読み取り、Booking が扱える候補選択肢（Leg ドラフト付き）へ変換する。
 * Booking は Routing のドメイン型に依存せず、この ACL の DTO を境界とする（BC 独立性・ADR-007/008）。
 */
export interface RouteCandidateAcl {
  findCandidates(query: RouteCandidateQuery): Promise<RouteCandidateOption[]>;
}
