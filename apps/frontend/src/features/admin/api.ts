import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type { LockedAccount } from './types'

/** いまロックされているアカウント（US32-1）。期限切れは含まれない。 */
export function fetchLockedAccounts(): Promise<LockedAccount[]> {
  return apiClient.get<LockedAccount[]>(API_PATHS.lockedAccounts)
}

/**
 * ロックを解除する（US32-2）。
 *
 * 解除した管理者は**サーバが利用者ヘッダから取る**。画面から送らない——送ると、
 * 画面を書き換えて別人の名前で記録できてしまう。
 */
export function unlockAccount(username: string): Promise<LockedAccount> {
  return apiClient.post<LockedAccount>(API_PATHS.unlockAccount(username), {})
}
