import type { CargoSnapshot } from '../../../domain/model/cargo-snapshot.js';

/**
 * 貨物スナップショット取得 ACL ポート（腐敗防止層）。
 * 追跡番号から Booking の貨物情報（出発港・目的港・旅程・経路状態）を Handling 固有の
 * CargoSnapshot として取得する。Booking のドメイン型には依存しない（BC 独立性・ADR-007/008 パターン）。
 */
export interface CargoSnapshotAcl {
  findByTrackingNumber(trackingNumber: string): Promise<CargoSnapshot | null>;
}
