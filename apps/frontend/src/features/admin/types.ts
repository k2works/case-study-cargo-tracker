/**
 * ロックされたアカウント 1 件（US32-1）。
 *
 * **パスワードもメールアドレスも受け取らない。** 解除の判断に要るのは「誰が・いつまで
 * ロックされているか」だけである。要らないものを持つと、画面の不具合でそのまま漏れる。
 */
export type LockedAccount = {
  username: string
  displayName: string
  failedAttempts: number
  /** ロック期限（ISO 8601）。この時刻を過ぎると自動で解除される。 */
  lockedUntil: string
}
