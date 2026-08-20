import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../config/api'
import { MOCK_USERS } from './users'

type MockShipper = {
  id: number
  shipperCode: string
  type: 'INDIVIDUAL' | 'CORPORATE'
  name: string
  email: string
  address: string
  phone: string | null
}

const shippers: MockShipper[] = []
let sequence = 0

/**
 * 連続失敗によるロック（US31）。本物と同じ回数・同じ応答で振る舞う。
 *
 * ここを実装しないと、画面が「5 回間違えると入れない」ことを一度も通らないまま
 * 「実装済み」になる。モックは仕様の写しであって、都合のよい相手ではない。
 */
const MAX_FAILED_ATTEMPTS = 5
const failedAttempts = new Map<string, number>()

/** 各シナリオを独立させるための取り消し口。本番の API には存在しない。 */
export function resetLoginAttempts() {
  failedAttempts.clear()
}

function isLocked(userId: string) {
  return (failedAttempts.get(userId) ?? 0) >= MAX_FAILED_ATTEMPTS
}

export const handlers = [
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

  http.get(API_PATHS.shippers, ({ request }) => {
    const keyword = new URL(request.url).searchParams.get('keyword')
    if (keyword === null || keyword.trim() === '') {
      return HttpResponse.json(shippers)
    }
    const lower = keyword.toLowerCase()
    return HttpResponse.json(
      shippers.filter(
        (s) => s.name.toLowerCase().includes(lower) || s.email.toLowerCase().includes(lower),
      ),
    )
  }),

  http.post(API_PATHS.shippers, async ({ request }) => {
    const body = (await request.json()) as MockShipper & { registerAnyway: boolean }
    const existing = shippers.find((s) => s.email === body.email)

    if (existing !== undefined && !body.registerAnyway) {
      return HttpResponse.json(
        { message: '同じメールアドレスの荷主が既に登録されています', existing },
        { status: 409 },
      )
    }

    sequence += 1
    const created: MockShipper = {
      id: sequence,
      shipperCode: `SHP-${String(sequence).padStart(6, '0')}`,
      type: body.type,
      name: body.name,
      email: body.email,
      address: body.address,
      phone: body.phone ?? null,
    }
    shippers.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
]
