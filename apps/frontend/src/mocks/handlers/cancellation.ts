/**
 * 輸送中の予約キャンセル（US30・UC22）のモック。
 *
 * <p><strong>本物と同じ規則で拒む。</strong>モックだけが甘いと、画面は「動く」まま
 * 本番で落ちる。
 *
 * <p>写した本物の規則:
 * <ul>
 *   <li>US30-2 — <strong>輸送開始前は即時に確定する。</strong>承認を挟むのは、貨物が
 *       船の上にあってどこで降ろすかを決めないとキャンセルできないからであり、
 *       まだ動いていない貨物には要らない
 *   <li>[ADR-025] 決定 4 — <strong>陸揚げ地の候補は「現在地の港」と「次の寄港地」</strong>。
 *       全港から選ばせない。候補に無い港での承認は断る
 *   <li>US30-5 — 承認には陸揚げ地が必須
 *   <li>US30-7 — <strong>却下しても予約は輸送中のまま</strong>維持される
 * </ul>
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { LOCATIONS, bookings } from '../data'
import { formatBusinessDateTime } from '../../lib/business-time'

/** 予約状態の読み方。**画面に対訳表を置かない**。 */
const BOOKING_STATUS_LABELS: Record<string, string> = {
  PRELIMINARY: '仮予約',
  ROUTE_PROPOSED: '経路提案中',
  ROUTE_NOTIFIED: '荷主へ通知済',
  CONFIRMED: '確定済',
  TRACKING_ISSUED: '追跡番号発行済',
  IN_TRANSIT: '輸送中',
  DELIVERED: '配送完了',
  CANCELLED: 'キャンセル',
}

const CANCELLATION_STATUS_LABELS: Record<string, string> = {
  REQUESTED: '承認待ち',
  APPROVED: '承認済',
  REJECTED: '却下',
}

type MockCancellation = {
  cancellationId: number
  bookingId: string
  reason: string
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED'
  requestedBy: string
  requestedAt: string
  bookingStatusAtRequest: string
  dischargeLocationUnLocode: string | null
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
}

export const cancellations: MockCancellation[] = []

let cancellationIdSequence = 0

export function resetCancellations() {
  cancellations.length = 0
  cancellationIdSequence = 0
}

function nameOf(unLocode: string | null) {
  if (unLocode === null) return null
  return LOCATIONS.find((location) => location.unLocode === unLocode)?.name ?? unLocode
}

function view(cancellation: MockCancellation) {
  return {
    ...cancellation,
    // **本物と同じ形で返す**（業務タイムゾーンの「YYYY-MM-DD HH:mm」）。
    // 生の ISO を返すと、モックの上でだけ画面が違って見える
    requestedAt: formatBusinessDateTime(cancellation.requestedAt),
    decidedAt:
      cancellation.decidedAt === null
        ? null
        : formatBusinessDateTime(cancellation.decidedAt),
    statusLabel: CANCELLATION_STATUS_LABELS[cancellation.status] ?? cancellation.status,
    bookingStatusAtRequestLabel:
      BOOKING_STATUS_LABELS[cancellation.bookingStatusAtRequest]
      ?? cancellation.bookingStatusAtRequest,
    dischargeLocationName: nameOf(cancellation.dischargeLocationUnLocode),
  }
}

/**
 * 陸揚げ地の候補（[ADR-025] 決定 4）。
 *
 * **現在地の港**（最後の荷役地点）と、**旅程の残りの荷降し地**。
 * 全港から選ばせると、船が寄らない港を指定でき、荷降しできない約束を荷主にすることになる。
 */
function dischargeCandidatesOf(bookingId: string) {
  const booking = bookings.find((candidate) => candidate.bookingId === bookingId)
  if (booking === undefined) {
    return []
  }
  const candidates: { unLocode: string; name: string; reason: string }[] = []
  const current = booking.lastHandlingLocationUnLocode ?? null
  if (current !== null) {
    candidates.push({
      unLocode: current,
      name: nameOf(current) as string,
      reason: '現在地の港',
    })
  }
  for (const leg of booking.itinerary ?? []) {
    if (candidates.some((candidate) => candidate.unLocode === leg.unloadUnLocode)) {
      continue
    }
    candidates.push({
      unLocode: leg.unloadUnLocode,
      name: leg.unloadName,
      reason: '次の寄港地',
    })
  }
  return candidates
}

function activeOf(bookingId: string) {
  return cancellations.find(
    (candidate) => candidate.bookingId === bookingId && candidate.status === 'REQUESTED',
  )
}

export const cancellationHandlers = [
  http.get(API_PATHS.cancellations, () =>
    HttpResponse.json(
      cancellations
        .filter((cancellation) => cancellation.status === 'REQUESTED')
        // 古い申請から片付ける。放っておくほど貨物は目的地へ近づく
        .sort((a, b) => a.requestedAt.localeCompare(b.requestedAt))
        .map((cancellation) => ({
          ...view(cancellation),
          dischargeCandidates: dischargeCandidatesOf(cancellation.bookingId),
        })),
    ),
  ),

  // **予約の下の経路は、一覧の経路より後に置いても構わない。**衝突するのは
  // `/api/v1/bookings/{bookingId}` の側であり、一覧を別の接頭辞へ移して解いた
  http.get('/api/v1/bookings/:bookingId/cancellation', ({ params }) => {
    const found = cancellations
      .filter((candidate) => candidate.bookingId === String(params.bookingId))
      .at(-1)
    return HttpResponse.json(found === undefined ? null : view(found))
  }),

  http.post('/api/v1/bookings/:bookingId/cancellation', async ({ params, request }) => {
    const bookingId = String(params.bookingId)
    const body = (await request.json()) as { reason: string }
    const booking = bookings.find((candidate) => candidate.bookingId === bookingId)
    if (booking === undefined) {
      return HttpResponse.json({ message: '予約が見つかりません' }, { status: 404 })
    }
    // **理由は必須。**あとから「なぜ止めたのか」を読むのは、荷主に説明する担当者である
    if (body.reason === undefined || body.reason.trim() === '') {
      return HttpResponse.json({ message: 'キャンセルの理由を入力してください' }, { status: 400 })
    }
    if (booking.bookingStatus === 'CANCELLED') {
      return HttpResponse.json(
        { message: 'この予約はすでにキャンセルされています' },
        { status: 409 },
      )
    }
    if (activeOf(bookingId) !== undefined) {
      return HttpResponse.json(
        { message: 'この予約には承認待ちのキャンセル申請があります' },
        { status: 409 },
      )
    }

    // **輸送中かどうかで扱いが変わる**（US30-2）。まだ動いていない貨物に承認は要らない
    const inTransit = booking.bookingStatus === 'IN_TRANSIT'
    cancellationIdSequence += 1
    const cancellation: MockCancellation = {
      cancellationId: cancellationIdSequence,
      bookingId,
      reason: body.reason,
      status: inTransit ? 'REQUESTED' : 'APPROVED',
      requestedBy: 'sales01',
      requestedAt: new Date().toISOString(),
      bookingStatusAtRequest: booking.bookingStatus,
      dischargeLocationUnLocode: null,
      decidedBy: inTransit ? null : 'sales01',
      decidedAt: inTransit ? null : new Date().toISOString(),
      decisionReason: inTransit ? null : '輸送開始前のため即時に確定しました',
    }
    cancellations.push(cancellation)
    if (!inTransit) {
      booking.bookingStatus = 'CANCELLED'
    }
    return HttpResponse.json(
      { request: view(cancellation), awaitingApproval: inTransit },
      { status: 201 },
    )
  }),

  http.put('/api/v1/bookings/:bookingId/cancellation/approve', async ({ params, request }) => {
    const bookingId = String(params.bookingId)
    const body = (await request.json()) as {
      dischargeLocationUnLocode: string
      decisionReason: string
    }
    const cancellation = activeOf(bookingId)
    const booking = bookings.find((candidate) => candidate.bookingId === bookingId)
    if (cancellation === undefined || booking === undefined) {
      return HttpResponse.json(
        { message: '承認待ちのキャンセル申請が見つかりません' },
        { status: 404 },
      )
    }
    // **陸揚げ地は必須**（US30-5）。どこで降ろすかを決めないとキャンセルできない
    if (
      body.dischargeLocationUnLocode === undefined
      || body.dischargeLocationUnLocode.trim() === ''
    ) {
      return HttpResponse.json({ message: '陸揚げ地を指定してください' }, { status: 400 })
    }
    // **候補に無い港での承認は断る**（[ADR-025] 決定 4）。船が寄らない港を指定できると、
    // 荷降しできない約束を荷主にすることになる
    if (
      dischargeCandidatesOf(bookingId).every(
        (candidate) => candidate.unLocode !== body.dischargeLocationUnLocode,
      )
    ) {
      return HttpResponse.json(
        { message: 'その港では荷降しできません。候補から選んでください' },
        { status: 400 },
      )
    }
    cancellation.status = 'APPROVED'
    cancellation.dischargeLocationUnLocode = body.dischargeLocationUnLocode
    cancellation.decidedBy = 'tracker01'
    cancellation.decidedAt = new Date().toISOString()
    cancellation.decisionReason = body.decisionReason
    booking.bookingStatus = 'CANCELLED'
    return HttpResponse.json(view(cancellation))
  }),

  http.put('/api/v1/bookings/:bookingId/cancellation/reject', async ({ params, request }) => {
    const bookingId = String(params.bookingId)
    const body = (await request.json()) as { decisionReason: string }
    const cancellation = activeOf(bookingId)
    if (cancellation === undefined) {
      return HttpResponse.json(
        { message: '承認待ちのキャンセル申請が見つかりません' },
        { status: 404 },
      )
    }
    // **却下の理由は必須**（US30-7）。理由は申請者と荷主に伝わる
    if (body.decisionReason === undefined || body.decisionReason.trim() === '') {
      return HttpResponse.json({ message: '却下の理由を入力してください' }, { status: 400 })
    }
    cancellation.status = 'REJECTED'
    cancellation.decidedBy = 'tracker01'
    cancellation.decidedAt = new Date().toISOString()
    cancellation.decisionReason = body.decisionReason
    // **予約は輸送中のまま維持される。**却下は「キャンセルしない」という決定である
    return HttpResponse.json(view(cancellation))
  }),
]
