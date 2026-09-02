import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type { LoginResult } from '../../stores/auth-store'

export type LoginRequest = {
  userId: string
  password: string
}

/**
 * ログイン。認証情報誤り・アカウントロック中・無効化アカウントはすべて 401 で返り、
 * 文言も同一である（アカウントの存在有無を攻撃者に教えない。US31）。
 */
export function login(request: LoginRequest): Promise<LoginResult> {
  return apiClient.post<LoginResult>(API_PATHS.login, request)
}
