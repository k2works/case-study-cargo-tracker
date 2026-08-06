/**
 * 荷主存在確認 ACL ポート（Booking → Shipper Context）。
 * Booking Context は Shipper Context に直接依存せず、このポート経由で荷主の存在を確認する
 * （domain-model ビジネスルール 9・BC 独立性）。
 */
export interface ShipperExistenceChecker {
  /** 荷主コード（SHP-XXXX）から荷主のサロゲート ID を解決する。存在しなければ null */
  resolveShipperId(shipperCode: string): Promise<number | null>;
}

/** 荷主が存在しない場合のドメインエラー（US04） */
export class ShipperNotFoundError extends Error {
  constructor(readonly shipperCode: string) {
    super(`指定された荷主が見つかりません: ${shipperCode}`);
    this.name = 'ShipperNotFoundError';
  }
}
