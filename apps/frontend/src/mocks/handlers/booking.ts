/**
 * 貨物予約のモック（US04〜US06・US09〜US14）。
 *
 * <p><strong>本物と同じ規則で拒む。</strong>モックだけが甘いと、画面は「動く」まま本番で落ちる。
 * 規則を写すときは、本物の該当箇所（集約のメソッド）を開いて条件を読み比べる（IT5 の Try 4）。
 *
 * <p>認可（403）はここでは再現しない。ブラウザは利用者ヘッダを送らず、それを付けるのは
 * Gateway だからである。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import {
  businessDateEndInstant,
  businessDateStartInstant,
} from '../../lib/business-time'
import {
  type MockBooking,
  BOOKING_LIMIT,
  LOCATIONS,
  bookings,
  nextMockTrackingNumber,
  sequenceState,
  shippers,
  todayAt,
  withShipperName,
} from '../data'
import { type MockLeg, findMockRoutes, voyages } from '../routes'

export const bookingHandlers = [
  http.get(API_PATHS.bookingLocations, () =>
    HttpResponse.json(LOCATIONS.map(({ unLocode, name }) => ({ unLocode, name }))),
  ),

  // 分類の表示名はサーバが持つ（画面に対訳表を置くと直しが 2 箇所に分かれる）
  http.get(API_PATHS.bookingHazardClasses, () =>
    HttpResponse.json([
      { code: '1', label: '火薬類' },
      { code: '2', label: '高圧ガス' },
      { code: '3', label: '引火性液体' },
      { code: '4', label: '可燃性物質（可燃性固体・自然発火性物質・水反応可燃性物質）' },
      { code: '5', label: '酸化性物質・有機過酸化物' },
      { code: '6', label: '毒物・病毒をうつしやすい物質' },
      { code: '7', label: '放射性物質' },
      { code: '8', label: '腐食性物質' },
      { code: '9', label: '有害性物質' },
    ]),
  ),

  http.get(`${API_PATHS.bookings}/:bookingId`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    return found === undefined
      ? HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
      : HttpResponse.json(withShipperName(found))
  }),


  http.post(`${API_PATHS.bookings}/:bookingId/routing-request`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.routingStatus !== 'NOT_ROUTED') {
      return HttpResponse.json(
        { message: 'この予約はすでに経路設計を依頼しています' },
        { status: 409 },
      )
    }
    found.routingStatus = 'ROUTING_REQUESTED'
    return HttpResponse.json(withShipperName(found))
  }),

  // 条件協議の差し戻し（US10・ADR-020 決定 7）。本物と同じ規則で拒む
  http.post(`${API_PATHS.bookings}/:bookingId/consultation-request`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.routingStatus !== 'ROUTING_REQUESTED') {
      return HttpResponse.json(
        { message: '経路設計を依頼された予約だけが、条件の協議を営業へ戻せます' },
        { status: 409 },
      )
    }
    found.routingStatus = 'CONSULTATION_REQUESTED'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 経路の割り当て（US09・ADR-019）。
   *
   * 本物と同じ規則で拒む。モックだけが甘いと、画面は「動く」まま本番で落ちる。
   * 認可（403）はここでは再現しない。ブラウザは利用者ヘッダを送らず、それを付けるのは
   * Gateway だからである。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/route`, async ({ params, request }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }

    const body = (await request.json()) as { legs?: MockLeg[]; maxTransshipments?: number }
    const legs = body.legs ?? []
    if (legs.length === 0) {
      return HttpResponse.json(
        { message: '割り当てる経路の区間を指定してください' },
        { status: 400 },
      )
    }
    // ADR-020 決定 1・4: 引き渡された予約か、すでに経路が決まった予約にだけ割り当てられる。
    // 本物は「それ以外」を拒む。ここで NOT_ROUTED だけを見ると、差し戻し中の予約を
    // モックだけが通し、実物でだけ 409 になる
    // **誤配のあとの組み直しも通す**（US28-4・[ADR-026] 決定 4b）。弾くと、
    // 画面には [経路を再設計する] が出るのに押した先で 409 になる
    if (
      found.routingStatus !== 'ROUTING_REQUESTED' &&
      found.routingStatus !== 'ROUTED' &&
      found.routingStatus !== 'MISROUTED'
    ) {
      return HttpResponse.json(
        { message: '経路設計を依頼された予約にだけ経路を割り当てられます' },
        { status: 409 },
      )
    }
    // ADR-021 決定 3: 確定したあとは差し替えられない。差し替えを許すと、
    // 「確定から経路設計へ戻せない」を裏口から破ることになる。
    // **誤配のあとの組み直しは別の操作**であり、この制限の対象ではない（決定 4b）
    if (
      found.routingStatus !== 'MISROUTED' &&
      (found.bookingStatus === 'CONFIRMED' || found.bookingStatus === 'TRACKING_ISSUED')
    ) {
      return HttpResponse.json(
        {
          message:
            '確定した予約の経路は差し替えられません。変更が必要なら担当者に相談してください',
        },
        { status: 409 },
      )
    }

    // ADR-019 決定 2: 選んだ経路がいまも算出できるかを確かめる。
    // 確かめずに通すと、欠航した航海の旅程が予約に入る
    const deadlineInstant = businessDateEndInstant(found.arrivalDeadline)
    const now = new Date().toISOString()
    const usable = voyages.filter(
      (voyage) => voyage.supportedCargoTypes.includes(found.type) && voyage.departureTime >= now,
    )
    const stillAvailable = findMockRoutes(
      usable,
      found.originUnLocode,
      found.destinationUnLocode,
      deadlineInstant,
      body.maxTransshipments ?? 2,
      found.departureDate === null ? null : businessDateStartInstant(found.departureDate),
    ).some(
      (candidate) =>
        candidate.length === legs.length &&
        candidate.every(
          (leg, index) =>
            leg.voyageNumber === legs[index].voyageNumber &&
            leg.fromUnLocode === (legs[index] as unknown as { loadUnLocode: string }).loadUnLocode &&
            leg.toUnLocode ===
              (legs[index] as unknown as { unloadUnLocode: string }).unloadUnLocode &&
            // 本物は時刻まで含めて等価判定する。ここで見ないと、時刻がずれた旅程が
            // 画面テストでは通り、実物でだけ 409 になる
            leg.departureTime === (legs[index] as unknown as { loadTime: string }).loadTime &&
            leg.arrivalTime === (legs[index] as unknown as { unloadTime: string }).unloadTime,
        ),
    )
    if (!stillAvailable) {
      return HttpResponse.json(
        {
          message:
            '選んだ経路はもう使えません。航海スケジュールが変わっている可能性があります。経路をもう一度探してください',
        },
        { status: 409 },
      )
    }

    // 地点の名称はサーバがマスタから引く（画面が送った名称を信じない）
    found.itinerary = legs.map((leg) => {
      const load = (leg as unknown as { loadUnLocode: string }).loadUnLocode
      const unload = (leg as unknown as { unloadUnLocode: string }).unloadUnLocode
      return {
        voyageNumber: leg.voyageNumber,
        loadUnLocode: load,
        loadName: LOCATIONS.find((location) => location.unLocode === load)?.name ?? load,
        unloadUnLocode: unload,
        unloadName: LOCATIONS.find((location) => location.unLocode === unload)?.name ?? unload,
        loadTime: (leg as unknown as { loadTime: string }).loadTime,
        unloadTime: (leg as unknown as { unloadTime: string }).unloadTime,
      }
    })
    // **誤配のあとは別の操作である**（[ADR-026] 決定 4b）。通常の割り当てを通すと、
    // 輸送中の貨物が「経路を提示した」状態へ戻り、荷主が合意した記録が消える
    const wasMisrouted = found.routingStatus === 'MISROUTED'
    found.routingStatus = 'ROUTED'
    if (!wasMisrouted) {
      found.bookingStatus = 'ROUTE_PROPOSED'
    }
    // 差し替えたら通知の記録は消える。残すと、画面は「通知しました」と出したまま
    // 経路だけが変わり、営業は変わったことに気づかない
    found.routeNotifiedAt = null
    found.routeNotifiedBy = null

    // **期限を超えるなら、何日超えるかを返す**（US28-6）。本物は目的地の暦で判断する
    // ——ここでは日付だけを比べる（モックは時差を持たない）
    const arrival = found.itinerary?.at(-1)?.unloadTime ?? null
    const beyond =
      arrival === null
        ? null
        : Math.max(
            0,
            Math.round(
              (Date.parse(arrival.slice(0, 10)) -
                Date.parse(found.arrivalDeadline)) /
                (24 * 60 * 60 * 1000),
            ),
          )
    return HttpResponse.json({
      ...withShipperName(found),
      daysBeyondDeadline: beyond === null || beyond <= 0 ? null : beyond,
    })
  }),

  /**
   * 予約の日程の訂正（US06 の訂正）。
   *
   * 本物（`Cargo#reviseSchedule`）の条件を読み比べて写した。**引き渡す前か、営業へ戻された
   * 予約だけ**が直せる。経路設計者が組んでいる最中に条件が変わると、出来上がった経路が
   * 条件を満たさなくなる。直せるのは日程だけである。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/schedule`, async ({ params, request }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (
      found.routingStatus !== 'NOT_ROUTED' &&
      found.routingStatus !== 'CONSULTATION_REQUESTED'
    ) {
      return HttpResponse.json(
        { message: '経路設計に引き渡す前か、営業へ戻された予約だけを直せます' },
        { status: 409 },
      )
    }
    const body = (await request.json()) as {
      departureDate?: string | null
      arrivalDeadline?: string | null
    }
    const arrivalDeadline = body.arrivalDeadline ?? ''
    if (arrivalDeadline === '') {
      return HttpResponse.json({ message: '到着期限は必須です' }, { status: 400 })
    }
    // 本物は目的地の暦で「今日」を決める（ADR-010）。モックも同じ地点の暦で判断する
    const destinationZone =
      LOCATIONS.find((location) => location.unLocode === found.destinationUnLocode)?.timeZone
      ?? 'Asia/Tokyo'
    if (arrivalDeadline < todayAt(destinationZone)) {
      return HttpResponse.json(
        { message: `到着期限に過去の日付は指定できません: ${arrivalDeadline}` },
        { status: 400 },
      )
    }
    if (
      (body.departureDate ?? '') !== '' &&
      (body.departureDate as string) > arrivalDeadline
    ) {
      return HttpResponse.json(
        { message: '希望出発日が到着期限より後になっています' },
        { status: 400 },
      )
    }
    found.departureDate = (body.departureDate ?? '') === '' ? null : (body.departureDate as string)
    found.arrivalDeadline = arrivalDeadline
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 荷主への通知（US12・ADR-021 決定 1・決定 2）。
   *
   * 本物（`Cargo#notifyShipper`）の条件を読み比べて写した。**経路が決まった予約か、
   * すでに通知した予約**だけが通知でき、再通知は記録を最新で上書きする。
   * ここを緩くすると、画面は「動く」まま本物でだけ 409 になる。
   */
  http.post(`${API_PATHS.bookings}/:bookingId/route-notification`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'ROUTE_PROPOSED' && found.bookingStatus !== 'ROUTE_NOTIFIED') {
      return HttpResponse.json(
        { message: '経路が決まった予約だけを荷主へ通知できます' },
        { status: 409 },
      )
    }
    found.bookingStatus = 'ROUTE_NOTIFIED'
    // 記録は最新で上書きする（ADR-021 決定 2。履歴は US19 まで持たない）
    found.routeNotifiedAt = new Date().toISOString()
    found.routeNotifiedBy = 'sales01'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 予約の確定（US13-2・ADR-021 決定 1）。
   *
   * **通知していない予約は確定できない。** 確定は「荷主の合意を得た」という業務上の
   * 事実であり、提示していない条件で合意は成り立たない。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/confirm`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'ROUTE_NOTIFIED') {
      return HttpResponse.json(
        { message: '荷主へ通知した予約だけを確定できます' },
        { status: 409 },
      )
    }
    found.bookingStatus = 'CONFIRMED'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 経路設計へ戻す（US13-4・ADR-021 決定 3・決定 4）。
   *
   * **経路の状態も戻す。** `bookingStatus` だけ戻しても経路設計者の作業待ちに現れず、
   * 荷主が変更を希望したことが誰にも伝わらない。**旅程は消さない**（見直しの起点になる）。
   * **確定したあとは戻せない**（決定 3）。
   */
  http.put(`${API_PATHS.bookings}/:bookingId/return-to-routing`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'ROUTE_NOTIFIED') {
      return HttpResponse.json(
        { message: '荷主へ通知した予約だけを経路設計へ戻せます' },
        { status: 409 },
      )
    }
    found.bookingStatus = 'ROUTE_PROPOSED'
    found.routingStatus = 'ROUTING_REQUESTED'
    return HttpResponse.json(withShipperName(found))
  }),

  /**
   * 追跡番号の発行（US14）。
   *
   * **確定した予約にだけ発行でき、二重には発行しない**（番号が変わると、荷主に伝えた
   * 番号で追えなくなる）。形式は本物と同じ `TRK-yyyyMMdd-nnnn` にする。
   * 形式が違うと、画面の表示崩れが実物でだけ出る。
   */
  http.post(`${API_PATHS.bookings}/:bookingId/tracking-number`, ({ params }) => {
    const found = bookings.find((booking) => booking.bookingId === params.bookingId)
    if (found === undefined) {
      return HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 })
    }
    if (found.bookingStatus !== 'CONFIRMED') {
      return HttpResponse.json(
        { message: '確定した予約にだけ追跡番号を発行できます' },
        { status: 409 },
      )
    }
    // null も未設定も「未発行」。項目を省いた種データが 409 になった（画面で踏んだのと同じ形）
    if ((found.trackingNumber ?? null) !== null) {
      return HttpResponse.json(
        { message: 'この予約はすでに追跡番号を発行しています' },
        { status: 409 },
      )
    }
    found.trackingNumber = nextMockTrackingNumber()
    found.bookingStatus = 'TRACKING_ISSUED'
    // 貨物はまだ動いていない（US14-3）
    found.transportStatus = 'NOT_RECEIVED'
    return HttpResponse.json(withShipperName(found))
  }),


  http.get(API_PATHS.bookings, ({ request }) => {
    const params = new URL(request.url).searchParams
    const type = params.get('type')
    const routingStatus = params.get('routingStatus')
    const bookingStatus = params.get('bookingStatus')
    const keyword = (params.get('keyword') ?? '').trim().toLowerCase()

    const matched = bookings.filter((booking) => {
      if (type !== null && booking.type !== type) {
        return false
      }
      // 経路設計待ちだけを見る絞り込み（US06）
      if (routingStatus !== null && booking.routingStatus !== routingStatus) {
        return false
      }
      // 追跡番号の発行待ちだけを見る絞り込み（US13-3）。本物と同じ条件で絞る
      if (bookingStatus !== null && booking.bookingStatus !== bookingStatus) {
        return false
      }
      if (keyword === '') {
        return true
      }
      const shipper = shippers.find((s) => s.id === booking.shipperId)
      return (
        booking.bookingId.toLowerCase().includes(keyword) ||
        (shipper?.name ?? '').toLowerCase().includes(keyword)
      )
    })

    // 新しい順。登録順だと、今入れた 1 件が常に最下部に沈む
    // 荷主名はサーバが結合して返す。モックだけが返さないと、画面の列が
    // モックのときだけ空欄になり「実装していない」ように見える
    const newestFirst = [...matched].reverse().map((booking) => ({
      ...booking,
      shipperName: shippers.find((s) => s.id === booking.shipperId)?.name ?? null,
    }))
    return HttpResponse.json({
      bookings: newestFirst.slice(0, BOOKING_LIMIT),
      totalCount: matched.length,
      limit: BOOKING_LIMIT,
      truncated: matched.length > BOOKING_LIMIT,
    })
  }),

  http.post(API_PATHS.bookings, async ({ request }) => {
    const body = (await request.json()) as Omit<
      MockBooking,
      'id' | 'bookingId' | 'bookingStatus' | 'transportStatus' | 'routingStatus'
      | 'originName' | 'destinationName'
    >

    // 本物と同じ規則で拒む。モックだけが甘いと、画面は「動く」まま本番で落ちる
    const invalid = (message: string) => HttpResponse.json({ message }, { status: 400 })

    if (!shippers.some((s) => s.id === body.shipperId)) {
      return invalid(`指定された荷主が見つかりません: ${body.shipperId}`)
    }
    const origin = LOCATIONS.find((l) => l.unLocode === body.originUnLocode)
    const destination = LOCATIONS.find((l) => l.unLocode === body.destinationUnLocode)
    if (origin === undefined) {
      return invalid(`出発地が見つかりません: ${body.originUnLocode}`)
    }
    if (destination === undefined) {
      return invalid(`目的地が見つかりません: ${body.destinationUnLocode}`)
    }
    if (origin.unLocode === destination.unLocode) {
      return invalid(`出発地と目的地は同じにできません: ${origin.unLocode}`)
    }
    if (body.arrivalDeadline < todayAt(destination.timeZone)) {
      return invalid(`到着期限に過去の日付は指定できません: ${body.arrivalDeadline}`)
    }
    if (!(body.weightKg > 0)) {
      return invalid(`重量は 0 より大きい値で指定してください: ${body.weightKg}`)
    }

    const hasDeclaration = body.unNumber !== null || body.hazardousClass !== null
    const hasTemperature = body.minCelsius !== null || body.maxCelsius !== null
    if (body.type === 'HAZARDOUS' && !hasDeclaration) {
      return invalid('危険物には危険物申告が必要です')
    }
    if (body.type !== 'HAZARDOUS' && hasDeclaration) {
      return invalid('危険物申告は危険物にだけ設定できます')
    }
    if (body.type === 'REFRIGERATED' && !hasTemperature) {
      return invalid('冷凍・冷蔵貨物には保管温度の条件が必要です')
    }
    if (body.type !== 'REFRIGERATED' && hasTemperature) {
      return invalid('保管温度の条件は冷凍・冷蔵貨物にだけ設定できます')
    }
    if (body.type === 'HAZARDOUS' && !/^UN\d{4}$/.test(body.unNumber ?? '')) {
      return invalid(`UN 番号の形式が不正です（UN + 4 桁）: ${body.unNumber}`)
    }
    if (
      body.type === 'REFRIGERATED' &&
      (body.minCelsius === null || body.maxCelsius === null)
    ) {
      return invalid('保管温度の下限と上限はどちらも必須です')
    }
    if (
      body.minCelsius !== null &&
      body.maxCelsius !== null &&
      body.minCelsius > body.maxCelsius
    ) {
      return invalid(
        `保管温度の下限が上限を超えています: ${body.minCelsius} > ${body.maxCelsius}`,
      )
    }

    sequenceState.booking += 1
    const created: MockBooking = {
      ...body,
      id: sequenceState.booking,
      // 予約番号の形式は契約になる（ADR-011）
      bookingId: `BKG-2026${String(sequenceState.booking).padStart(6, '0')}`,
      bookingStatus: 'PRELIMINARY',
      transportStatus: 'NOT_RECEIVED',
      routingStatus: 'NOT_ROUTED',
      originName: origin.name,
      destinationName: destination.name,
    }
    bookings.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
]
