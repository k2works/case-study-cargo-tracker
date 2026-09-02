/**
 * 通関申告のモック（US29・UC21）。
 *
 * <p><strong>本物と同じ規則で拒む。</strong>モックだけが甘いと、画面は「動く」まま
 * 本番で落ちる（IT9 返済枠 0.3 で実際に起きた——甘いモックは、モック自身の欠陥ではなく
 * <strong>画面が本物の規則を満たしていないこと</strong>を隠していた）。
 *
 * <p>写した本物の規則（[ADR-025](../../../../docs/adr/025-customs-declaration-and-cancellation-approval.md)）:
 * <ul>
 *   <li>決定 7 — 未決着（PENDING / HELD）の申告があるあいだ、2 件目は登録できない。
 *       REJECTED のあとは出し直せる。CLEARED のあとは断る
 *   <li>US29-2 — 状態の更新には理由が必須
 *   <li>US29-6 — 留置 3 日超の判定は<strong>最新の HELD 遷移日時</strong>から数える
 * </ul>
 *
 * <p>認可（403）はここでは再現しない。ブラウザは利用者ヘッダを送らず、それを付けるのは
 * Gateway だからである。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { bookings } from '../data'
import { formatBusinessDateTime } from '../../lib/business-time'

/** 通関状態（本物の `CustomsStatus` の写し）。 */
const CUSTOMS_STATUSES = [
  { status: 'PENDING', label: '審査中' },
  { status: 'CLEARED', label: '通関済' },
  { status: 'HELD', label: '留置' },
  { status: 'REJECTED', label: '不可' },
]

/** 留置がこの日数を超えたら督促の対象（US29-6）。本物と同じ値にする。 */
const HELD_OVERDUE_DAYS = 3

type MockStatusChange = {
  fromStatus: string
  toStatus: string
  changedBy: string
  changedAt: string
  reason: string
}

type MockDeclaration = {
  declarationId: number
  declarationNumber: string
  bookingId: string
  trackingNumber: string
  declaredAt: string
  status: string
  clearedAt: string | null
  remarks: string | null
  history: MockStatusChange[]
}

/** 一覧の上限。**本物（`ManageCustomsDeclarationUseCase.SEARCH_LIMIT`）と同じ値**。 */
const SEARCH_LIMIT = 200

export const customsDeclarations: MockDeclaration[] = []

let declarationIdSequence = 0

export function resetCustomsDeclarations() {
  customsDeclarations.length = 0
  declarationIdSequence = 0
}

function labelOf(status: string) {
  return CUSTOMS_STATUSES.find((candidate) => candidate.status === status)?.label ?? status
}

/**
 * 留置になってからの経過日数。
 *
 * **最新の HELD 遷移日時から数える**（data-model.md の注）。申告日時から数えると、
 * いったん通関して留め直された申告が、初日から 3 日超と判定される。
 */
function heldDaysOf(declaration: MockDeclaration): number | null {
  if (declaration.status !== 'HELD') {
    return null
  }
  const lastHeld = [...declaration.history].reverse().find((change) => change.toStatus === 'HELD')
  const from = lastHeld === undefined ? declaration.declaredAt : lastHeld.changedAt
  const elapsed = Date.now() - Date.parse(from)
  return Math.floor(elapsed / (24 * 60 * 60 * 1000))
}

/**
 * 日時は<strong>本物と同じ形</strong>で返す（業務タイムゾーンの「YYYY-MM-DD HH:mm」）。
 *
 * <p>生の ISO 文字列を返すと、**モックの上でだけ画面が違って見える**——利用者には
 * `2027-09-03T00:00:00.000Z` が出る。マニュアルのキャプチャはモックで撮るため、
 * その姿が手引きに載る（IT9 のクローズで実際に撮れてしまった）。
 */
function businessTime(isoInstant: string | null): string | null {
  return isoInstant === null ? null : formatBusinessDateTime(isoInstant)
}

function view(declaration: MockDeclaration) {
  const heldDays = heldDaysOf(declaration)
  return {
    declarationId: declaration.declarationId,
    declarationNumber: declaration.declarationNumber,
    bookingId: declaration.bookingId,
    trackingNumber: declaration.trackingNumber,
    declaredAt: businessTime(declaration.declaredAt),
    status: declaration.status,
    statusLabel: labelOf(declaration.status),
    clearedAt: businessTime(declaration.clearedAt),
    heldOverdue: heldDays !== null && heldDays > HELD_OVERDUE_DAYS,
    heldDays,
    remarks: declaration.remarks,
  }
}

function detailView(declaration: MockDeclaration) {
  return {
    ...view(declaration),
    history: declaration.history.map((change) => ({
      fromStatus: change.fromStatus,
      fromStatusLabel: labelOf(change.fromStatus),
      toStatus: change.toStatus,
      toStatusLabel: labelOf(change.toStatus),
      changedBy: change.changedBy,
      changedAt: businessTime(change.changedAt),
      reason: change.reason,
    })),
  }
}

/** 未決着（審査中・留置）の申告。**高々 1 件**（[ADR-025] 決定 7）。 */
function unsettledOf(trackingNumber: string) {
  return customsDeclarations.find(
    (candidate) =>
      candidate.trackingNumber === trackingNumber &&
      (candidate.status === 'PENDING' || candidate.status === 'HELD'),
  )
}

export const customsHandlers = [
  http.get(`${API_PATHS.customs}/statuses`, () => HttpResponse.json(CUSTOMS_STATUSES)),

  http.get(`${API_PATHS.customs}/overdue`, () =>
    HttpResponse.json({
      count: customsDeclarations.filter((declaration) => view(declaration).heldOverdue).length,
    }),
  ),

  http.get(`${API_PATHS.customs}/:declarationId`, ({ params }) => {
    const declaration = customsDeclarations.find(
      (candidate) => candidate.declarationId === Number(params.declarationId),
    )
    if (declaration === undefined) {
      return HttpResponse.json({ message: '通関申告が見つかりません' }, { status: 404 })
    }
    return HttpResponse.json(detailView(declaration))
  }),

  http.get(API_PATHS.customs, ({ request }) => {
    const params = new URL(request.url).searchParams
    const bookingId = params.get('bookingId')
    const trackingNumber = params.get('trackingNumber')
    const status = params.get('status')
    // **未決着（審査中・留置）だけ。**朝の待ち行列（US29-7）
    const unsettledOnly = params.get('unsettledOnly') === 'true'

    const matched = customsDeclarations
      .filter((declaration) => bookingId === null || declaration.bookingId === bookingId)
      .filter(
        (declaration) =>
          trackingNumber === null || declaration.trackingNumber === trackingNumber,
      )
      .filter((declaration) => status === null || declaration.status === status)
      .filter(
        (declaration) =>
          !unsettledOnly || declaration.status === 'PENDING' || declaration.status === 'HELD',
      )
      // 留置が長いものを先に。担当者が毎朝この一覧を上から見る
      .sort((a, b) => (heldDaysOf(b) ?? -1) - (heldDaysOf(a) ?? -1))

    // **本物と同じ形で返す**（総件数と切り捨て）。黙って切ると「全件見た」と受け取られる
    return HttpResponse.json({
      declarations: matched.slice(0, SEARCH_LIMIT).map(view),
      totalCount: matched.length,
      limit: SEARCH_LIMIT,
      truncated: matched.length > SEARCH_LIMIT,
    })
  }),

  http.post(API_PATHS.customs, async ({ request }) => {
    const body = (await request.json()) as Record<string, string | null>
    const booking = bookings.find((candidate) => candidate.trackingNumber === body.trackingNumber)
    if (booking === undefined) {
      return HttpResponse.json(
        { message: '指定された追跡番号の貨物が見つかりません。番号を確かめてください' },
        { status: 404 },
      )
    }
    if (body.declarationNumber === null || body.declarationNumber?.trim() === '') {
      return HttpResponse.json({ message: '申告番号を入力してください' }, { status: 400 })
    }
    const declaredAt = body.declaredAt
    if (declaredAt === null || declaredAt === undefined || declaredAt.trim() === '') {
      return HttpResponse.json({ message: '申告日時を指定してください' }, { status: 400 })
    }
    if (Number.isNaN(Date.parse(declaredAt)) || !declaredAt.includes('T')) {
      return HttpResponse.json(
        { message: '申告日時は ISO 8601（2026-08-23T09:00:00Z）の形式で指定してください' },
        { status: 400 },
      )
    }
    // **未決着は高々 1 件**（[ADR-025] 決定 7）。2 件あると、CLAIM のガードが
    // どちらの申告を見ればよいか決まらない
    if (unsettledOf(body.trackingNumber as string) !== undefined) {
      return HttpResponse.json(
        {
          message:
            'この貨物には決着していない通関申告があります。先にその申告を処理してください',
        },
        { status: 409 },
      )
    }
    // **通関済のあとに出し直さない。**引き取れる状態を、あとからの申告で覆さない
    if (
      customsDeclarations.some(
        (candidate) =>
          candidate.trackingNumber === body.trackingNumber && candidate.status === 'CLEARED',
      )
    ) {
      return HttpResponse.json(
        { message: 'この貨物はすでに通関済です。申告を出し直すことはできません' },
        { status: 409 },
      )
    }

    declarationIdSequence += 1
    const declaration: MockDeclaration = {
      declarationId: declarationIdSequence,
      declarationNumber: body.declarationNumber as string,
      bookingId: booking.bookingId,
      trackingNumber: body.trackingNumber as string,
      declaredAt,
      status: 'PENDING',
      clearedAt: null,
      remarks: body.remarks ?? null,
      // 登録も履歴に残す。**from も NOT NULL**（初回は PENDING）
      history: [
        {
          fromStatus: 'PENDING',
          toStatus: 'PENDING',
          changedBy: 'handler01',
          changedAt: declaredAt,
          reason: '申告を登録しました',
        },
      ],
    }
    customsDeclarations.push(declaration)
    return HttpResponse.json(detailView(declaration), { status: 201 })
  }),

  http.put(`${API_PATHS.customs}/:declarationId/status`, async ({ params, request }) => {
    const body = (await request.json()) as { status: string; reason: string }
    const declaration = customsDeclarations.find(
      (candidate) => candidate.declarationId === Number(params.declarationId),
    )
    if (declaration === undefined) {
      return HttpResponse.json({ message: '通関申告が見つかりません' }, { status: 404 })
    }
    // **理由は必須**（US29-2）。空で通すと、監査の履歴が「誰かが変えた」だけになる
    if (body.reason === undefined || body.reason.trim() === '') {
      return HttpResponse.json({ message: '変更の理由を入力してください' }, { status: 400 })
    }
    if (CUSTOMS_STATUSES.every((candidate) => candidate.status !== body.status)) {
      return HttpResponse.json(
        { message: `通関状態が不正です: ${body.status}` },
        { status: 400 },
      )
    }
    const changedAt = new Date().toISOString()
    declaration.history.push({
      fromStatus: declaration.status,
      toStatus: body.status,
      changedBy: 'tracker01',
      changedAt,
      reason: body.reason,
    })
    declaration.status = body.status
    declaration.clearedAt = body.status === 'CLEARED' ? changedAt : null
    return HttpResponse.json(detailView(declaration))
  }),
]
