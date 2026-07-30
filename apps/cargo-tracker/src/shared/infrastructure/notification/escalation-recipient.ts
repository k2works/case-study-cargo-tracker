/**
 * 管理職エスカレーションの通知宛先（ADR-012）。
 * 実運用で監視される宛先に差し替え可能なよう、環境変数 ESCALATION_RECIPIENT で上書きできる。
 * 未設定時はデモ用の固定スタブを用いる（実配信は運用フェーズで差し替える）。
 */
export function escalationRecipient(): string {
  return process.env.ESCALATION_RECIPIENT ?? 'management@example.com';
}
