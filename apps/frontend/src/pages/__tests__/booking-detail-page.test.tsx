import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import type { Role } from '../../types/role'
import { BookingDetailPage } from '../booking-detail-page'
import { mockAvailableActions } from '../../mocks/data'

const BOOKING = {
  id: 1,
  bookingId: 'BKG-2026000001',
  shipperId: 1,
  shipperName: '丸紅商事株式会社',
  bookingStatus: 'PRELIMINARY',
  transportStatus: 'NOT_RECEIVED',
  routingStatus: 'NOT_ROUTED',
  type: 'GENERAL',
  weightKg: 1200,
  quantity: 20,
  description: '電子部品',
  lengthCm: null,
  widthCm: null,
  heightCm: null,
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  departureDate: null,
  arrivalDeadline: '2026-09-20',
  hazardousClass: null,
  unNumber: null,
  properShippingName: null,
  minCelsius: null,
  maxCelsius: null,
  // 本物は必ず返す項目。省くと、モックだけが違う分岐を通る
  routeNotifiedAt: null,
  routeNotifiedBy: null,
  trackingNumber: null,
}

/**
 * 状態を指定して予約の応答を作る。
 *
 * 行える操作は**状態から導く**（本物と同じ規則）。テストが availableActions を手で並べると、
 * 集約・画面・モックに続く 4 つ目の写しになり、規則が変わっても気づけない。
 */
function bookingIn(overrides: Partial<typeof BOOKING> = {}) {
  const merged = { ...BOOKING, ...overrides }
  return { ...merged, availableActions: mockAvailableActions(merged as never) }
}


/** 状態から、行える操作を導く。テストが操作を手で並べると 4 つ目の写しになる。 */
function withActions<T extends typeof BOOKING>(booking: T) {
  return { ...booking, availableActions: mockAvailableActions(booking as never) }
}

function renderPage(roles: Role[] = ['ROLE_SALES']) {
  loginAs(roles)
  return renderWithProviders(<BookingDetailPage />, ['/booking/BKG-2026000001'], undefined, {
    path: '/booking/:bookingId',
  })
}

describe('予約の詳細（US06）', () => {
  beforeEach(() => {
    server.use(
      http.get(`${API_PATHS.bookings}/:bookingId`, () => HttpResponse.json(bookingIn())),
    )
  })

  /** 中身が見えないまま引き渡すと、経路設計者は不備に気づけないまま経路を組む。 */
  it('出発地・目的地・期限・貨物仕様を確認できる', async () => {
    renderPage()

    expect(await screen.findByText(/BKG-2026000001/)).toBeInTheDocument()
    expect(screen.getByText(/Tokyo（JPTYO）/)).toBeInTheDocument()
    expect(screen.getByText(/Los Angeles（USLAX）/)).toBeInTheDocument()
    expect(screen.getByText('2026-09-20')).toBeInTheDocument()
    expect(screen.getByText('1200 kg')).toBeInTheDocument()
    expect(screen.getByText('電子部品')).toBeInTheDocument()
    expect(screen.getByText('丸紅商事株式会社')).toBeInTheDocument()
  })

  /** 生の英字を出すと、利用者は自分の予約がどうなっているか読めない。 */
  it('状態は日本語で示す', async () => {
    renderPage()

    expect(await screen.findByText('仮受付')).toBeInTheDocument()
    expect(screen.getByText('未依頼')).toBeInTheDocument()
  })

  it('営業担当者は経路設計を依頼できる', async () => {
    server.use(
      http.post(`${API_PATHS.bookings}/:bookingId/routing-request`, () =>
        HttpResponse.json(bookingIn({ routingStatus: 'ROUTING_REQUESTED' })),
      ),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '経路設計を依頼する' }))

    expect(await screen.findByText(/経路設計を依頼しました/)).toBeInTheDocument()
  })

  /**
   * 経路設計者は依頼のボタンを持たない。
   *
   * 立てられると、引き渡しの記録が「誰が渡したか」を表さなくなる。
   */
  it('経路設計者には依頼のボタンを出さない', async () => {
    renderPage(['ROLE_ROUTING'])

    await screen.findByText(/BKG-2026000001/)
    expect(screen.queryByRole('button', { name: '経路設計を依頼する' })).not.toBeInTheDocument()
  })

  it('引き渡し済みの予約には依頼のボタンを出さない', async () => {
    server.use(
      http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json(bookingIn({ routingStatus: 'ROUTING_REQUESTED' })),
      ),
    )
    renderPage()

    expect(await screen.findByText(/すでに引き渡し済みです/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '経路設計を依頼する' })).not.toBeInTheDocument()
  })

  /** 409 は入力の誤りではない。「入力を直してください」と伝えると利用者は直す先を探す。 */
  it('依頼できない状態のときは、その理由をそのまま見せる', async () => {
    server.use(
      http.post(`${API_PATHS.bookings}/:bookingId/routing-request`, () =>
        HttpResponse.json(
          { message: 'この予約はすでに経路設計を依頼しています' },
          { status: 409 },
        ),
      ),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '経路設計を依頼する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'この予約はすでに経路設計を依頼しています',
    )
  })

  it('見つからない予約は、一覧へ戻れる形で伝える', async () => {
    server.use(
      http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json({ message: '指定された予約が見つかりません' }, { status: 404 }),
      ),
    )
    renderPage()

    expect(await screen.findByText(/予約を表示できませんでした/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '貨物予約の一覧に戻る' })).toBeInTheDocument()
  })

  /**
   * 状態軸の到達性（IT4）。
   *
   * 引き渡された予約からだけ経路設計へ行ける。引き渡されていない予約に入口を出すと、
   * サーバが同じ判定で詳細を絞っているため、押した先で 403 になる。
   */
  describe('経路設計への入口', () => {
    it('引き渡された予約からは経路設計へ行ける', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json(bookingIn({ routingStatus: 'ROUTING_REQUESTED' })),
        ),
      )
      renderPage(['ROLE_ROUTING'])

      expect(await screen.findByRole('link', { name: '経路を割り当て' })).toHaveAttribute(
        'href',
        '/routing/design/BKG-2026000001',
      )
    })

    it('引き渡されていない予約には入口を出さない', async () => {
      renderPage(['ROLE_ROUTING'])

      await screen.findByText(/BKG-2026000001/)
      expect(screen.queryByRole('link', { name: '経路を割り当て' })).not.toBeInTheDocument()
      expect(screen.getByText(/まだ経路設計に引き渡されていません/)).toBeInTheDocument()
    })

    it('営業担当者には経路設計の入口を出さない（経路を組むのは経路設計者の仕事）', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json(bookingIn({ routingStatus: 'ROUTING_REQUESTED' })),
        ),
      )
      renderPage(['ROLE_SALES'])

      await screen.findByText(/BKG-2026000001/)
      expect(screen.queryByRole('link', { name: '経路を割り当て' })).not.toBeInTheDocument()
    })
  })

  describe('割り当て経路（旅程・US09）', () => {
    const ROUTED = withActions({
      ...BOOKING,
      routingStatus: 'ROUTED',
      bookingStatus: 'ROUTE_PROPOSED',
      itinerary: [
        {
          voyageNumber: 'V0201',
          loadUnLocode: 'JPTYO',
          loadName: 'Tokyo',
          unloadUnLocode: 'CNSHA',
          unloadName: 'Shanghai',
          loadTime: '2026-09-02T00:00:00Z',
          unloadTime: '2026-09-04T00:00:00Z',
        },
        {
          voyageNumber: 'V0202',
          loadUnLocode: 'CNSHA',
          loadName: 'Shanghai',
          unloadUnLocode: 'USLAX',
          unloadName: 'Los Angeles',
          loadTime: '2026-09-05T00:00:00Z',
          unloadTime: '2026-09-18T00:00:00Z',
        },
      ],
    })

    it('積み替えを含む全区間を運ぶ順に出す', async () => {
      server.use(http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json(ROUTED)))
      renderPage()

      // 航海番号 1 つだけでは積み替えのある経路が表せない。荷役・追跡が見るのは
      // 「どの港で積み替えるか」であり、そこが読めないと問い合わせに答えられない
      expect(await screen.findByText(/割り当て経路（旅程・2 区間）/)).toBeInTheDocument()
      expect(screen.getByText('V0201')).toBeInTheDocument()
      expect(screen.getByText('V0202')).toBeInTheDocument()
      // 港は名前で、コードは併記にとどめる
      expect(screen.getAllByText(/Shanghai/).length).toBeGreaterThan(0)
    })

    it('経路が決まっていない予約では枠ごと出さない', async () => {
      renderPage()

      await screen.findByText(/Tokyo/)
      // 空の表を出すと「区間が 0 件の旅程がある」ように見える
      expect(screen.queryByText(/割り当て経路/)).not.toBeInTheDocument()
    })

    it('経路設計者は決まった経路を見直せる', async () => {
      server.use(http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json(ROUTED)))
      renderPage(['ROLE_ROUTING'])

      // 航海の遅延・欠航で差し替えることがある（ADR-020 決定 4）。
      // 決まったら終わりにすると、差し替えの入口がどこにも無くなる
      expect(await screen.findByRole('link', { name: '経路を見直す' })).toHaveAttribute(
        'href',
        '/routing/design/BKG-2026000001',
      )
    })
  })

  /**
   * 予約の日程の訂正（US06 の訂正・IT6 タスク 0.11）。
   *
   * <p>条件協議の結果が「期限を延ばす」だったとき、直せないと再依頼しても同じ結果になる。
   */
  describe('日程の訂正', () => {
    it('引き渡す前の予約は、営業が日程を直せる', async () => {
      renderPage()

      await userEvent.click(await screen.findByRole('button', { name: '日程を直す' }))

      expect(screen.getByLabelText('到着期限')).toBeInTheDocument()
      expect(screen.getByLabelText('出発希望日（任意）')).toBeInTheDocument()
    })

    /**
     * <strong>直せる範囲を画面が言う。</strong>
     *
     * <p>言わないと、営業は出発地の誤りもここで直せると思って探すことになる。
     */
    it('直せるのは日程だけであることを伝える', async () => {
      renderPage()

      expect(await screen.findByText(/出発地・目的地・貨物の内容は直せません/))
        .toBeInTheDocument()
    })

    /** 経路設計者が組んでいる最中に条件が変わると、出来上がった経路が条件を満たさなくなる。 */
    it('引き渡し済みの予約には、訂正の入口を出さない', async () => {
      server.use(http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json(bookingIn({ routingStatus: 'ROUTING_REQUESTED' }))))
      renderPage()

      expect(await screen.findByText(/BKG-2026000001/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '日程を直す' })).not.toBeInTheDocument()
    })

    /** 差し戻された予約こそ直したい。ここを塞ぐと協議の結果を反映できない。 */
    it('営業へ戻された予約は直せる', async () => {
      server.use(http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json(bookingIn({ routingStatus: 'CONSULTATION_REQUESTED' }))))
      renderPage()

      expect(await screen.findByRole('button', { name: '日程を直す' })).toBeInTheDocument()
    })

    it('経路設計者には訂正の入口を出さない', async () => {
      renderPage(['ROLE_ROUTING'])

      expect(await screen.findByText(/BKG-2026000001/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '日程を直す' })).not.toBeInTheDocument()
    })

    /** 断られた理由は、サーバの言葉をそのまま見せる（入力の誤りを利用者が直せるように）。 */
    it('直せなかったときは理由をそのまま見せる', async () => {
      server.use(
        http.put(`${API_PATHS.bookings}/:bookingId/schedule`, () =>
          HttpResponse.json(
            { message: '到着期限に過去の日付は指定できません: 2020-01-01' },
            { status: 400 },
          )),
      )
      renderPage()

      await userEvent.click(await screen.findByRole('button', { name: '日程を直す' }))
      await userEvent.click(screen.getByRole('button', { name: '日程を保存する' }))

      expect(await screen.findByRole('alert'))
        .toHaveTextContent('到着期限に過去の日付は指定できません')
    })
  })

  /**
   * 荷主への通知・確定・発行（US12〜US14・[ADR-021]）。
   *
   * ここで確かめるのは<strong>誰にどの操作を出すか</strong>である。できる／できないの
   * 判定はサーバの集約が持つ。
   */
  describe('通知・確定・発行', () => {
    const ROUTED = withActions({
      ...BOOKING,
      routingStatus: 'ROUTED',
      bookingStatus: 'ROUTE_PROPOSED',
      routeNotifiedAt: null,
      routeNotifiedBy: null,
      trackingNumber: null,
      itinerary: [
        {
          voyageNumber: 'V0100',
          loadUnLocode: 'JPTYO',
          loadName: 'Tokyo',
          unloadUnLocode: 'USLAX',
          unloadName: 'Los Angeles',
          loadTime: '2026-09-02T00:00:00Z',
          unloadTime: '2026-09-18T00:00:00Z',
        },
      ],
    })

    const NOTIFIED = withActions({
      ...ROUTED,
      bookingStatus: 'ROUTE_NOTIFIED',
      routeNotifiedAt: '2026-08-22T02:00:00Z',
      routeNotifiedBy: 'sales01',
    })

    /**
     * サーバの状態を 1 つ持ち、操作で書き換える。
     *
     * 固定の応答を返すと、操作したあとの取り直しで<strong>古い状態が返り続ける</strong>。
     * 画面は正しく取り直しているのに、テストだけが「変わらない」と言うことになる。
     */
    let current: Record<string, unknown> = {}

    function given(booking: Record<string, unknown>) {
      current = booking
      server.use(http.get(`${API_PATHS.bookings}/:bookingId`, () =>
        HttpResponse.json(current)))
    }

    function respondsWith(path: string, method: 'post' | 'put', next: Record<string, unknown>) {
      const handler = () => {
        current = next
        return HttpResponse.json(current)
      }
      server.use(method === 'post' ? http.post(path, handler) : http.put(path, handler))
    }

    /** [ADR-021] 決定 6。出さないと、どれが自分の仕事か分からない。 */
    it('いまの状態で誰の手番かを出す', async () => {
      given(ROUTED)
      renderPage()

      expect(await screen.findByText(/荷主へ通知してください/)).toBeInTheDocument()
    })

    it('荷主の返事待ちであることを出す', async () => {
      given(NOTIFIED)
      renderPage()

      expect(await screen.findByText(/荷主の手番です/)).toBeInTheDocument()
    })

    /**
     * <strong>メールが送られないことを画面に書く。</strong>
     *
     * 送ったことにして黙っていると、営業は荷主に届いたと思い込む。
     */
    it('メールが送られないことを、通知の操作のそばに書く', async () => {
      given(ROUTED)
      renderPage()

      expect(await screen.findByText(/この操作ではメールは送られません/))
        .toBeInTheDocument()
    })

    /** US12-2。確認せずに送れる形にすると、営業は送ってから旅程を見ることになる。 */
    it('送る前に、経由港・所要日数・到着予定を確認できる', async () => {
      given(ROUTED)
      renderPage()

      expect(await screen.findByText('経由港')).toBeInTheDocument()
      expect(screen.getByText('直行（積み替えなし）')).toBeInTheDocument()
      expect(screen.getByText('所要日数')).toBeInTheDocument()
      expect(screen.getByText('到着予定')).toBeInTheDocument()
    })

    it('営業は荷主へ通知できる', async () => {
      given(ROUTED)
      respondsWith(`${API_PATHS.bookings}/:bookingId/route-notification`, 'post', NOTIFIED)
      renderPage()

      await userEvent.click(await screen.findByRole('button', { name: '荷主へ通知する' }))

      expect(await screen.findByText(/荷主へ通知しました/)).toBeInTheDocument()
    })

    /** [ADR-021] 決定 2。返事が無い・連絡先を間違えた、は実務で起きる。 */
    it('通知済みの予約はもう一度通知できる', async () => {
      given(NOTIFIED)
      renderPage()

      expect(await screen.findByRole('button', { name: 'もう一度通知する' }))
        .toBeInTheDocument()
    })

    /**
     * [ADR-021] 決定 1。
     *
     * <strong>押せるのにできない、を作らない。</strong>通知していない予約に確定の
     * ボタンを出すと、利用者は押してから断られることを毎回学び直す。
     */
    it('通知していない予約には確定のボタンを出さない', async () => {
      given(ROUTED)
      renderPage()

      expect(await screen.findByRole('button', { name: '荷主へ通知する' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '予約を確定する' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '経路設計へ戻す' })).not.toBeInTheDocument()
    })

    /**
     * <strong>押したら実際にその操作が起きる。</strong>
     *
     * <p>ボタンの存在だけを見ると、確定と戻すを取り違えても、どちらも何もしなくても
     * 緑のままになる（IT6 のクローズレビュー）。
     */
    it('通知した予約を確定すると、確定の要求が飛ぶ', async () => {
      given(NOTIFIED)
      let confirmed = false
      server.use(http.put(`${API_PATHS.bookings}/:bookingId/confirm`, () => {
        confirmed = true
        current = { ...NOTIFIED, bookingStatus: 'CONFIRMED' }
        return HttpResponse.json(current)
      }))
      renderPage()

      await userEvent.click(await screen.findByRole('button', { name: '予約を確定する' }))

      expect(await screen.findByText(/経路設計者の手番です/)).toBeInTheDocument()
      expect(confirmed).toBe(true)
    })

    it('経路設計へ戻すと、戻す要求が飛ぶ', async () => {
      given(NOTIFIED)
      let returned = false
      server.use(http.put(`${API_PATHS.bookings}/:bookingId/return-to-routing`, () => {
        returned = true
        current = { ...NOTIFIED, bookingStatus: 'ROUTE_PROPOSED',
          routingStatus: 'ROUTING_REQUESTED' }
        return HttpResponse.json(current)
      }))
      renderPage()

      await userEvent.click(await screen.findByRole('button', { name: '経路設計へ戻す' }))

      expect(returned).toBe(true)
    })

    /**
     * [ADR-021] 決定 3。確定した予約の経路は差し替えられない。
     *
     * <p>入口を出すと、候補を出し、選び、確認まで進んでから断られる。
     */
    it('確定した予約には経路を見直す入口を出さず、理由を示す', async () => {
      given(withActions({ ...NOTIFIED, bookingStatus: 'CONFIRMED' }))
      renderPage(['ROLE_ROUTING'])

      expect(await screen.findByText(/経路は差し替えられません/)).toBeInTheDocument()
      expect(screen.queryByRole('link', { name: '経路を見直す' })).not.toBeInTheDocument()
    })

    /** US13-4。戻したことが経路設計者に伝わることを、画面の言葉で示す。 */
    it('経路設計へ戻すと、経路設計者の一覧に出ることを伝える', async () => {
      given(NOTIFIED)
      renderPage()

      expect(await screen.findByText(/経路設計者の.*一覧に表示されます|「経路設計を待っている予約」に表示されます/))
        .toBeInTheDocument()
    })

    it('経路設計者には荷主とのやりとりの操作を出さない', async () => {
      given(NOTIFIED)
      renderPage(['ROLE_ROUTING'])

      expect(await screen.findByText(/BKG-2026000001/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '荷主へ通知する' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '予約を確定する' })).not.toBeInTheDocument()
    })

    /** US14。確定した予約にだけ出す。 */
    it('経路設計者は確定した予約に追跡番号を発行できる', async () => {
      const CONFIRMED = withActions({ ...NOTIFIED, bookingStatus: 'CONFIRMED' })
      given(CONFIRMED)
      respondsWith(`${API_PATHS.bookings}/:bookingId/tracking-number`, 'post', withActions({
        ...CONFIRMED,
        bookingStatus: 'TRACKING_ISSUED',
        trackingNumber: 'TRK-20260822-0001',
      }))
      renderPage(['ROLE_ROUTING'])

      await userEvent.click(
        await screen.findByRole('button', { name: '追跡番号を発行する' }))

      expect(await screen.findByText('TRK-20260822-0001')).toBeInTheDocument()
    })

    it('確定していない予約には発行のボタンを出さない', async () => {
      given(NOTIFIED)
      renderPage(['ROLE_ROUTING'])

      expect(await screen.findByText(/BKG-2026000001/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '追跡番号を発行する' }))
        .not.toBeInTheDocument()
    })

    it('営業には発行のボタンを出さない', async () => {
      const CONFIRMED = withActions({ ...NOTIFIED, bookingStatus: 'CONFIRMED' })
      given(CONFIRMED)
      renderPage(['ROLE_SALES'])

      expect(await screen.findByText(/BKG-2026000001/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '追跡番号を発行する' }))
        .not.toBeInTheDocument()
    })

    /**
     * US14-4 の代替。
     *
     * <strong>荷主には届かない。</strong>届いたと思われると、荷主からの問い合わせに
     * 営業が答えられなくなる。
     */
    it('発行した追跡番号は、荷主に届いていないことを添えて出す', async () => {
      given({
        ...NOTIFIED,
        bookingStatus: 'TRACKING_ISSUED',
        trackingNumber: 'TRK-20260822-0001',
      })
      renderPage()

      expect(await screen.findByText('TRK-20260822-0001')).toBeInTheDocument()
      expect(screen.getByText(/荷主には自動で送られていません/)).toBeInTheDocument()
    })
  })

  describe('誤配（US28-3・US28-4）', () => {
    const MISROUTED = {
      bookingStatus: 'IN_TRANSIT',
      routingStatus: 'MISROUTED',
      trackingNumber: 'TRK-20260823-0003',
      // **外れた場所と現在地を別の港にする。** 同じにすると、片方を落としても
      // もう片方が同じ文字列を出すため、検査が判別しない（最初にそう書いて空振りした）
      lastHandlingLocationUnLocode: 'HKHKG',
      // **本物と同じ形（ISO）で渡す。**画面が整形することを確かめる
      // ——「2027-09-09 09:00」を渡すと、整形を外しても緑になる
      misroute: { at: '2027-09-09T00:00:00Z', locationUnLocode: 'SGSIN' },
    }

    function renderMisrouted(roles: Role[]) {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json(bookingIn(MISROUTED as never)),
        ),
      )
      return renderPage(roles)
    }

    /**
     * **「誤配があった」だけでは足りない**（US28-3）。
     *
     * 経路設計者は**どこで外れたか**と**いまどこにいるか**が分からないと、
     * 組み直す起点を決められない。
     */
    it('外れた場所・日時と、現在地が出る', async () => {
      renderMisrouted(['ROLE_ROUTING'])

      const banner = await screen.findByRole('alert')
      expect(banner).toHaveTextContent('予定ルートから外れています')
      expect(banner, '外れた場所が出ていない。組み直す起点が分からない')
        .toHaveTextContent('SGSIN')
      expect(banner, '現在地が出ていない。いまどこから組み直すのか分からない')
        .toHaveTextContent('HKHKG')
      // 業務タイムゾーン（Asia/Tokyo）で 9 時。**生の ISO のままでは出さない**
      expect(banner, '日時が生の ISO のまま出ている。担当者が読み替えることになる')
        .toHaveTextContent('2027-09-09 09:00')
    })

    /**
     * **直すのは経路設計者である**（US28-4・[ADR-026] 決定 6）。
     *
     * サーバが操作を載せ、画面はそれに従う——**載せ忘れると画面には何も出ないが
     * API は 200 を返す**（IT9 で踏んだ形）。
     */
    it('経路設計者には、再設計への導線が出る', async () => {
      renderMisrouted(['ROLE_ROUTING'])

      expect(
        await screen.findByRole('link', { name: '経路を再設計する' }),
        '再設計の入口が無い。誤配に気づいても直せない',
      ).toHaveAttribute('href', '/routing/design/BKG-2026000001')
    })

    /** 営業は組み直さない。**押せない操作を見せない**。 */
    it('営業には、再設計への導線を出さない', async () => {
      renderMisrouted(['ROLE_SALES'])

      await screen.findByRole('alert')
      expect(
        screen.queryByRole('link', { name: '経路を再設計する' }),
      ).not.toBeInTheDocument()
    })

    /**
     * <strong>手番は全部の状態に言葉がある</strong>（[ADR-021] 決定 6）。
     *
     * <p>状態を足したときに書き足さないと、<strong>空の枠だけが出る</strong>
     * ——「何か出るはずのものが出ていない」と読まれる（IT10 のキャプチャで気づいた）。
     */
    it('輸送中の予約にも、手番の言葉が出る', async () => {
      // **誤配ではない輸送中**で見る。誤配で止まっている貨物は「荷役の記録で状態が
      // 進みます」ではない（次の検査が守る）
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json(
            bookingIn({ ...MISROUTED, routingStatus: 'ROUTED' } as never),
          ),
        ),
      )
      renderPage(['ROLE_ROUTING'])

      expect(
        await screen.findByText(/輸送中です/),
        '手番の言葉が無い。空の枠だけが出る',
      ).toBeInTheDocument()
    })

    /** 誤配していない予約にはバナーを出さない。**一覧が警告で埋まると読まれなくなる**。 */
    /**
     * US28-6。<strong>伝えるのは営業である</strong>（通知は代替）。
     *
     * <p>超過の日数が経路を割り当てた直後の画面にしか出ないと、経路設計者が
     * メモを取り損ねた時点で誰も荷主に伝えられない。<strong>営業が開く場所に残す。</strong>
     */
    it('期限を超えるなら、何日超えるかと、伝えるのが営業であることを出す', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json(
            bookingIn({ ...MISROUTED, daysBeyondDeadline: 5 } as never),
          ),
        ),
      )
      renderPage(['ROLE_SALES'])

      const alert = await screen.findByRole('alert')
      expect(alert, '何日超えるかが出ていない。荷主に説明できない')
        .toHaveTextContent('5 日超えます')
      expect(alert, '荷主へ自動で伝わると誤解される').toHaveTextContent(
        /自動で(は)?(通知|送)/,
      )
    })

    it('期限内なら、超過の案内を出さない', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json(
            bookingIn({ ...MISROUTED, daysBeyondDeadline: null } as never),
          ),
        ),
      )
      renderPage(['ROLE_SALES'])

      await screen.findByRole('alert')
      expect(screen.queryByText(/超えます/)).not.toBeInTheDocument()
    })

    /**
     * IT10 レビュー（user-representative 高 1・高 2）。<strong>読むだけで開ける。</strong>
     *
     * <p>誤配に最初に気づくのも、キャンセルを承認するのも追跡管理者である。例外一覧と
     * 承認一覧の両方からここへ渡す導線があり、承認の判断には荷主・貨物種別・旅程が要る。
     * <strong>操作は出さない</strong>——出すと、押した先でサーバに断られる。
     */
    it('追跡管理者は中身を読めるが、操作は出さない', async () => {
      renderMisrouted(['ROLE_TRACKER'])

      await screen.findByRole('alert')
      // 判断材料は読める
      expect(screen.getByText('丸紅商事株式会社')).toBeInTheDocument()
      // 操作は出さない
      expect(
        screen.queryByRole('link', { name: '経路を再設計する' }),
        '押した先でサーバに断られる操作を出している',
      ).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /確定する/ })).not.toBeInTheDocument()
    })

    /**
     * <strong>「一度でも外れた」と「いま外れている」は別である</strong>
     * （IT10 レビュー・3 視点が独立に指摘）。
     *
     * <p>記録は料金調整の根拠として残る（US28-8）が、組み直したあとに赤い指示が
     * 出続けると、経路設計者は<strong>済んだ仕事をやり直す</strong>。指示は状態で、
     * 記録の表示は事実で決める。
     */
    it('組み直したあとは、指示ではなく記録として残る', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json(
            bookingIn({
              ...MISROUTED,
              routingStatus: 'ROUTED',
            } as never),
          ),
        ),
      )
      renderPage(['ROLE_ROUTING'])

      await screen.findByRole('heading', { name: /BKG-2026000001/ })
      expect(
        screen.queryByText(/経路を組み直してください/),
        '組み直したのに、まだ組み直せと言っている',
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('link', { name: '経路を再設計する' }),
        '済んだ操作の入口が残っている',
      ).not.toBeInTheDocument()
      // **記録は残る**（US28-8。料金調整の根拠）
      expect(screen.getByText(/誤配の記録/)).toBeInTheDocument()
      expect(screen.getByText(/SGSIN/)).toBeInTheDocument()
    })

    /**
     * <strong>手番が実態と食い違わない</strong>（IT10 レビュー・user-representative 低 1）。
     *
     * <p>誤配で止まっている貨物は `IN_TRANSIT` のままなので、状態だけを見ると
     * 「輸送中です。荷役の記録で状態が進みます。」と出る。<strong>進まない</strong>
     * ——赤いバナーと矛盾して読める。
     */
    it('誤配で止まっているときは、輸送中の案内を出さない', async () => {
      renderMisrouted(['ROLE_ROUTING'])

      await screen.findByRole('alert')
      expect(
        screen.queryByText(/荷役の記録で状態が進みます/),
        '誤配で止まっているのに「進みます」と出ている',
      ).not.toBeInTheDocument()
      expect(screen.getByText(/経路設計者の手番です/)).toBeInTheDocument()
    })

    /**
     * <strong>営業には次にすることを書く</strong>（IT10 レビュー・user-representative 中 2）。
     *
     * <p>バナーは全ロールに出るが、再設計の入口は経路設計者にしか出ない。営業が読むと
     * 「組み直してください」と言われるだけで、誰が直すのかが分からない。
     */
    it('営業には、経路設計者が組み直すことを伝える', async () => {
      renderMisrouted(['ROLE_SALES'])

      const banner = await screen.findByRole('alert')
      expect(banner, '営業は次に何をすればよいか分からない')
        .toHaveTextContent('経路設計者が組み直します')
    })

    it('誤配していない予約には、バナーを出さない', async () => {
      renderPage(['ROLE_ROUTING'])

      await screen.findByRole('heading', { name: /^予約 BKG-/ })
      expect(screen.queryByText(/予定ルートから外れています/)).not.toBeInTheDocument()
    })
  })
})
