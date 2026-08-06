/**
 * アカウントロックポリシー。
 * 認証失敗が一定回数連続するとアカウントを一時ロックする（US26 受入基準）。
 */
export const MAX_FAILED_ATTEMPTS = 5;

/** 失敗回数がロック閾値に達しているか判定する */
export function isLockedByAttempts(failedAttempts: number): boolean {
  return failedAttempts >= MAX_FAILED_ATTEMPTS;
}

/** 認証失敗後の新しい失敗回数を返す */
export function nextFailedAttempts(current: number): number {
  return current + 1;
}
