/**
 * 精算スナップショット（値オブジェクト）。
 * BillingSnapshotAcl（腐敗防止層）経由で取得した輸送実績・荷主情報を Billing 固有の形で保持し、
 * 料金算出（GenerateInvoiceService）に使用する。
 * Booking / Shipper / Tracking のドメイン型には依存しない（BC 独立性・ADR-007/008）。
 */
export interface BillingSnapshot {
  readonly bookingId: string;
  /** 荷主識別子（請求書に保持する参照キー。荷主コード） */
  readonly shipperId: string;
  /** 荷主名（料金算出画面の表示用） */
  readonly shipperName: string;
  readonly shipperType: 'INDIVIDUAL' | 'CORPORATE';
  /** 契約割引率（0〜0.3。個人荷主は 0） */
  readonly discountRate: number;
  readonly weightKg: number;
  readonly cargoType: string;
  /** 旅程の所要日数（最初の load から最後の unload までの日数） */
  readonly transitDays: number;
  readonly origin: string;
  readonly destination: string;
  readonly bookingStatus: string;
  /** 未解決の追跡例外の種別リスト（料金調整の入力材料として表示する） */
  readonly activeExceptionTypes: readonly string[];
  /** 解決済みの追跡例外の種別リスト */
  readonly resolvedExceptionTypes: readonly string[];
}
