/**
 * 認証のモック（US26・US27・US31）。
 *
 * <p>失敗の理由は返さない。存在しない利用者・誤ったパスワード・無効化・ロック中を
 * 同じ応答にする（本物と同じ規則。US31）。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { failedAttempts, isLocked } from '../data'
import { MOCK_USERS } from '../users'

export const authHandlers = [
  http.post(API_PATHS.login, async ({ request }) => {
    const { userId, password } = (await request.json()) as { userId: string; password: string }
    const user = MOCK_USERS[userId]

    const failure = HttpResponse.json(
      { message: '利用者 ID またはパスワードが正しくありません' },
      { status: 401 },
    )

    // ロック中は正しいパスワードでも入れない。理由は返さない（US31）
    if (isLocked(userId)) {
      return failure
    }

    // 失敗の理由は返さない。存在しない利用者・誤ったパスワード・無効化を同じ応答にする（US31）
    if (user === undefined || user.password !== password || !user.enabled) {
      failedAttempts.set(userId, (failedAttempts.get(userId) ?? 0) + 1)
      return failure
    }

    failedAttempts.delete(userId)

    return HttpResponse.json({
      token: `mock-token-${userId}`,
      userId,
      displayName: user.displayName,
      roles: user.roles,
    })
  }),
]
