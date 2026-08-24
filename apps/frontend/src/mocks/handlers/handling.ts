/**
 * 荷役作業のモック（US15・US16）。
 *
 * <p><strong>本物と同じ規則で拒む。</strong>モックだけが甘いと、画面は「動く」まま本番で落ちる。
 * 規則を写すときは、本物の該当箇所を開いて条件を読み比べる（IT5 の Try 4）。
 *
 * <p>読み比べた本物:
 * <ul>
 *   <li>{@code HandlingType} — 種別ごとの要件（航海番号・荷受人の確認・照合する港）
 *   <li>{@code CargoSnapshot#isOffRoute} — 作業場所の照合。<strong>照らす相手が無いときは
 *       予定外に倒す</strong>
 *   <li>{@code RegisterHandlingActivityUseCase#requireCustomsCleared} — 引取の通関ガード
 *       （US29-3）。<strong>申告が無い貨物も断る</strong>
 *   <li>{@code HandlingActivity#register} — 断る条件（航海番号・荷受人の確認・作業者）
 *   <li>{@code HandlingActivityController} — 認可（記録は荷役作業員だけ、参照は追跡管理者にも）
 * </ul>
 *
 * <p>認可（403）はここでは再現しない。ブラウザは利用者ヘッダを送らず、それを付けるのは
 * Gateway だからである。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { bookings } from '../data'

/**
 * 荷役の種別と要件（本物の `HandlingType` の写し）。
 *
 * 積込・荷降しに航海番号が要るのは、どの船に載せたか分からないと貨物を追えないため。
 * 引取の荷受人確認は、通関ガード（US29・IT9）の代替である（[ADR-023] 決定 4）。
 */
const HANDLING_TYPES = [
  {
    type: 'RECEIVE',
    label: '受領',
    requiresVoyageNumber: false,
    requiresConsigneeConfirmation: false,
  },
  { type: 'LOAD', label: '積込', requiresVoyageNumber: true, requiresConsigneeConfirmation: false },
  {
    type: 'UNLOAD',
    label: '荷降し',
    requiresVoyageNumber: true,
    requiresConsigneeConfirmation: false,
  },
  {
    type: 'CLAIM',
    label: '引取',
    requiresVoyageNumber: false,
    requiresConsigneeConfirmation: true,
  },
]

type MockHandlingActivity = {
  id: number
  bookingId: string
  type: string
  locationUnLocode: string
  locationName: string
  completionTime: string
  operatorName: string
  voyageNumber: string | null
  consigneeConfirmation: string | null
  offRoute: boolean
}

export const handlingActivities: MockHandlingActivity[] = []

let handlingIdSequence = 0

/**
 * 作業場所が予定と違うか（本物の `CargoSnapshot#isOffRoute` の写し）。
 *
 * **照らす相手が無いときは予定外に倒す。** 旅程が決まる前に船へ積んで「予定どおり」と
 * 答えると、記録に何も残らない。
 */
function isOffRoute(booking: (typeof bookings)[number], type: string, unLocode: string): boolean {
  const legs = booking.itinerary ?? []
  switch (type) {
    case 'RECEIVE':
      return booking.originUnLocode !== unLocode
    case 'CLAIM':
      return booking.destinationUnLocode !== unLocode
    case 'LOAD':
      return !legs.some((leg) => leg.loadUnLocode === unLocode)
    case 'UNLOAD':
      return !legs.some((leg) => leg.unloadUnLocode === unLocode)
    default:
      return true
  }
}

export const handlingHandlers = [
  http.get(`${API_PATHS.handling}/types`, () => HttpResponse.json(HANDLING_TYPES)),

  // 作業場所は地点マスタから選ぶ（US15-3）。自由入力にすると綴りの揺れた港が記録に入る
  http.get(`${API_PATHS.handling}/locations`, async () => {
    const { LOCATIONS } = await import('../data')
    return HttpResponse.json(LOCATIONS.map(({ unLocode, name }) => ({ unLocode, name })))
  }),

  http.get(API_PATHS.handling, ({ request }) => {
    const params = new URL(request.url).searchParams
    const trackingNumber = params.get('trackingNumber')
    const bookingId =
      params.get('bookingId')
      ?? bookings.find((candidate) => candidate.trackingNumber === trackingNumber)?.bookingId

    // 追跡番号でも予約番号でも引けないなら、その貨物は無い
    if (bookingId === undefined) {
      return HttpResponse.json(
        { message: '指定された追跡番号の貨物が見つかりません。番号を確かめてください' },
        { status: 404 },
      )
    }

    return HttpResponse.json(
      handlingActivities
        .filter((activity) => activity.bookingId === bookingId)
        // 荷役は起きた順に読む。新しい順にすると「受領の前に積込がある」ように見える
        .sort((a, b) => a.completionTime.localeCompare(b.completionTime)),
    )
  }),

  http.post(API_PATHS.handling, async ({ request }) => {
    const body = (await request.json()) as Record<string, string | null>
    const booking = bookings.find((candidate) => candidate.trackingNumber === body.trackingNumber)
    if (booking === undefined) {
      return HttpResponse.json(
        { message: '指定された追跡番号の貨物が見つかりません。番号を確かめてください' },
        { status: 404 },
      )
    }

    const type = HANDLING_TYPES.find((candidate) => candidate.type === body.type)
    if (type === undefined) {
      return HttpResponse.json({ message: `荷役の種別が不正です: ${body.type}` }, { status: 400 })
    }
    // どの船に載せたか分からないと貨物を追えない
    if (type.requiresVoyageNumber && !body.voyageNumber) {
      return HttpResponse.json({ message: '航海番号は必須です' }, { status: 400 })
    }
    // 通関ガードの代替（[ADR-023] 決定 4）。空欄で通すと「通関前の貨物を引き渡した」記録が残る
    if (type.requiresConsigneeConfirmation && !body.consigneeConfirmation) {
      return HttpResponse.json({ message: '荷受人の確認は必須です' }, { status: 400 })
    }

    // 作業日時（本物の `HandlingActivityController#parseInstant` の写し）。
    // **本物にある検証がモックに無いと、画面のフォームは何を送っても通る。**
    // 実際、日時が空でも不正な形でもモックは 201 を返していた（IT9 返済枠 0.3）。
    const completionTime = body.completionTime
    if (completionTime === null || completionTime === undefined || completionTime.trim() === '') {
      return HttpResponse.json({ message: '作業日時を指定してください' }, { status: 400 })
    }
    // 入力値そのものは返さない（IT2 の決定）。何の項目が誤っているかだけを伝える
    if (Number.isNaN(Date.parse(completionTime)) || !completionTime.includes('T')) {
      return HttpResponse.json(
        { message: '作業日時は ISO 8601（2026-08-23T09:00:00Z）の形式で指定してください' },
        { status: 400 },
      )
    }

    // **通関が下りていない貨物は引き渡さない**（US29-3。本物の
    // `RegisterHandlingActivityUseCase#requireCustomsCleared` の写し）。
    //
    // **申告が 1 件も無い貨物も断る。**名簿方式の検査は「載っていないもの」を通すと、
    // 載せ忘れたものほど漏れる。申告が無いのは「検査の対象外」ではなく「通関済でない」
    if (type.type === 'CLAIM') {
      const { customsDeclarations } = await import('./customs')
      const latest = customsDeclarations
        .filter((declaration) => declaration.trackingNumber === body.trackingNumber)
        .at(-1)
      if (latest === undefined) {
        return HttpResponse.json(
          { message: 'この貨物には通関申告がありません。先に通関申告を登録してください' },
          { status: 409 },
        )
      }
      if (latest.status !== 'CLEARED') {
        const labels: Record<string, string> = {
          PENDING: '審査中',
          CLEARED: '通関済',
          HELD: '留置',
          REJECTED: '不可',
        }
        return HttpResponse.json(
          {
            message: `通関が完了していないため引き取りできません（現在: ${labels[latest.status] ?? latest.status}）`,
          },
          { status: 409 },
        )
      }
    }

    const { LOCATIONS } = await import('../data')
    const location = LOCATIONS.find(
      (candidate) => candidate.unLocode === body.locationUnLocode,
    )
    if (location === undefined) {
      return HttpResponse.json(
        { message: `作業場所が見つかりません: ${body.locationUnLocode}` },
        { status: 400 },
      )
    }

    handlingIdSequence += 1
    const activity: MockHandlingActivity = {
      id: handlingIdSequence,
      bookingId: booking.bookingId,
      type: type.type,
      locationUnLocode: location.unLocode,
      locationName: location.name,
      completionTime,
      operatorName: 'handler01',
      voyageNumber: body.voyageNumber ?? null,
      consigneeConfirmation: body.consigneeConfirmation ?? null,
      // 予定外でも拒まない（[ADR-023] 決定 3）
      offRoute: isOffRoute(booking, type.type, location.unLocode),
    }
    handlingActivities.push(activity)
    return HttpResponse.json(activity, { status: 201 })
  }),
]
