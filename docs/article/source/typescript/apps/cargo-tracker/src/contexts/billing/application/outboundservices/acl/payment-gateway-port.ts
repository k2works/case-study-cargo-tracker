/** 入金確認の結果（決済機関からの入金照会応答） */
export interface PaymentConfirmation {
  paidAt: Date;
  method: string;
  transactionReference: string;
}

/**
 * 決済機関連携ポート（US23-3・出力ポート / 腐敗防止層）。
 * 請求番号・請求金額をキーに入金を照会する。入金済みなら入金情報を、未入金なら null を返す。
 * 実際の決済機関接続は未実装のためスタブで代替する（nock は本プロジェクト未導入・ADR-007 のスタブ ACL パターン）。
 */
export interface PaymentGatewayPort {
  confirmPayment(invoiceNumber: string, amount: number): Promise<PaymentConfirmation | null>;
}
