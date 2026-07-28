import type { Shipper } from '../model/shipper.js';

/** Email 重複時に送出するドメインエラー（US02 受入基準）。既存荷主コードを提示に用いる */
export class EmailAlreadyRegisteredError extends Error {
  constructor(
    readonly email: string,
    readonly existingShipperCode: string,
  ) {
    super(`メールアドレスは既に登録されています: ${email}`);
    this.name = 'EmailAlreadyRegisteredError';
  }
}

/** 荷主リポジトリ出力ポート */
export interface ShipperRepository {
  /** 荷主を保存し、採番された ID を返す */
  save(shipper: Shipper): Promise<number>;
  /** Email で荷主を検索する（重複チェック・既存表示に用いる） */
  findByEmail(email: string): Promise<Shipper | null>;
}
