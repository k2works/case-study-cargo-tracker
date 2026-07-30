/**
 * 貨物スナップショット（値オブジェクト）。
 * CargoSnapshotAcl（腐敗防止層）経由で取得した Booking の貨物情報を Handling 固有の形で保持し、
 * 荷役妥当性検証（isValidFor）に使用する。Booking のドメイン型には依存しない（BC 独立性）。
 */
export interface CargoSnapshot {
  readonly bookingId: string;
  readonly origin: string;
  readonly destination: string;
  readonly itineraryLegs: readonly LegSnapshot[];
  readonly routingStatus: string;
}

/** 旅程区間スナップショット（値オブジェクト） */
export interface LegSnapshot {
  readonly loadLocation: string;
  readonly unloadLocation: string;
  readonly voyageNumber: string;
}
