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
