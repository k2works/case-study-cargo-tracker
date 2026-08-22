/**
 * 荷主のモック（US02・US03）。
 *
 * <p>登録と編集で同じ検査を通す。片方だけ甘くすると、緩いほうの入口から壊れた値が入る。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { type MockShipper, invalidShipperMessage, sequenceState, shippers } from '../data'

export const shipperHandlers = [
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

  http.get(`${API_PATHS.shippers}/:id`, ({ params }) => {
    const found = shippers.find((s) => s.id === Number(params.id))
    return found === undefined
      ? HttpResponse.json({ message: '指定された荷主が見つかりません' }, { status: 404 })
      : HttpResponse.json(found)
  }),

  // 編集（US02 / #550）。重複の問いかけは無い。すでにどの荷主かが分かっているため
  http.put(`${API_PATHS.shippers}/:id`, async ({ params, request }) => {
    const found = shippers.find((s) => s.id === Number(params.id))
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された荷主が見つかりません' }, { status: 404 })
    }

    const body = (await request.json()) as MockShipper
    // 種別は変えられない（本物と同じ規則）。黙って無視すると、原因と無関係な
    // 「契約番号が必要です」が返り、利用者は何度直しても通らない
    if (body.type !== found.type) {
      return HttpResponse.json(
        { message: '荷主種別は変更できません。種別が違うなら、それは別の荷主です' },
        { status: 400 },
      )
    }
    const invalid = invalidShipperMessage(body)
    if (invalid !== null) {
      return HttpResponse.json({ message: invalid }, { status: 400 })
    }

    // 荷主コードと id は変わらない。変わると、予約から見た荷主が別人になる
    found.name = body.name
    found.email = body.email
    found.address = body.address
    found.phone = body.phone ?? null
    found.contractNumber = body.contractNumber ?? null
    found.discountRatePercent = body.discountRatePercent ?? null
    return HttpResponse.json(found)
  }),

  http.post(API_PATHS.shippers, async ({ request }) => {
    const body = (await request.json()) as MockShipper & { registerAnyway: boolean }

    const invalid = invalidShipperMessage(body)
    if (invalid !== null) {
      return HttpResponse.json({ message: invalid }, { status: 400 })
    }

    const existing = shippers.find((s) => s.email === body.email)

    if (existing !== undefined && !body.registerAnyway) {
      return HttpResponse.json(
        { message: '同じメールアドレスの荷主が既に登録されています', existing },
        { status: 409 },
      )
    }

    sequenceState.shipper += 1
    const created: MockShipper = {
      id: sequenceState.shipper,
      shipperCode: `SHP-${String(sequenceState.shipper).padStart(6, '0')}`,
      type: body.type,
      name: body.name,
      email: body.email,
      address: body.address,
      phone: body.phone ?? null,
      contractNumber: body.contractNumber ?? null,
      discountRatePercent: body.discountRatePercent ?? null,
    }
    shippers.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
]
