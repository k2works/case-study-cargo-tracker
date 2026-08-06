/**
 * 荷主連絡先取得 ACL ポート（US12 是正・IT4 Try T1）。
 * Booking Context は Shipper Context のドメインモデルを import せず、
 * 通知宛先（荷主メール）の解決を本ポート経由で行う（BC 独立性）。
 */
export interface ShipperContactAcl {
  findEmailByShipperId(shipperId: number): Promise<string | null>;
}
