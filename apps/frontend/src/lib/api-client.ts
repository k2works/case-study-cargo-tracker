import { API_BASE_URL } from '../config/api'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let tokenProvider: () => string | null = () => null
let onUnauthorized: () => void = () => {}

/** 認証ストアからトークンを取得する関数を登録する。ストアへの依存を lib 側に持ち込まないため。 */
export function configureAuth(provider: () => string | null, unauthorizedHandler: () => void) {
  tokenProvider = provider
  onUnauthorized = unauthorizedHandler
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokenProvider()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (response.status === 401) {
    onUnauthorized()
    throw new ApiError(401, '認証が必要です')
  }

  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new ApiError(response.status, body.message ?? 'リクエストに失敗しました')
  }

  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
