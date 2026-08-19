import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../config/api'
import type { Role } from '../types/role'

type MockUser = { password: string; displayName: string; roles: Role[] }

/**
 * 開発・E2E 用の利用者。バックエンドが実装されるまでの仮の相手であり、
 * IT1 の Day 10 で実物のバックエンドに差し替える（それまで E2E は契約だけを検証する）。
 */
const USERS: Record<string, MockUser> = {
  sales01: { password: 'password', displayName: '山田太郎', roles: ['ROLE_SALES'] },
  tracker01: { password: 'password', displayName: '佐藤花子', roles: ['ROLE_TRACKER'] },
}

export const handlers = [
  http.post(API_PATHS.login, async ({ request }) => {
    const { userId, password } = (await request.json()) as { userId: string; password: string }
    const user = USERS[userId]

    // 失敗の理由は返さない。存在しない利用者と誤ったパスワードを同じ応答にする（US31）
    if (user === undefined || user.password !== password) {
      return HttpResponse.json(
        { message: '利用者 ID またはパスワードが正しくありません' },
        { status: 401 },
      )
    }

    return HttpResponse.json({
      token: `mock-token-${userId}`,
      userId,
      displayName: user.displayName,
      roles: user.roles,
    })
  }),
]
