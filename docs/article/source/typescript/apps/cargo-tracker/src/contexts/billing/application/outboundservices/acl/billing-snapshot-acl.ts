import type { BillingSnapshot } from '../../../domain/model/billing-snapshot.js';

/**
 * 精算スナップショット取得 ACL ポート（腐敗防止層）。
 * 予約 ID から輸送実績（経路・重量・貨物種別・所要日数・例外要約）と荷主情報（種別・割引率）を
 * Billing 固有の BillingSnapshot として取得する。
 * Booking / Shipper / Tracking のドメイン型には依存しない（BC 独立性・ADR-007/008 パターン）。
 */
export interface BillingSnapshotAcl {
  findByBookingId(bookingId: string): Promise<BillingSnapshot | null>;
}
