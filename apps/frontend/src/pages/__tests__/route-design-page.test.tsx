import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { RouteDesignPage } from '../route-design-page'

const BOOKING = {
  id: 1,
  bookingId: 'BKG-2026000001',
  shipperId: 1,
  shipperName: '丸紅商事',
  bookingStatus: 'PRELIMINARY',
  transportStatus: 'NOT_RECEIVED',
  routingStatus: 'ROUTING_REQUESTED',
  type: 'GENERAL',
  weightKg: 12000,
  quantity: 20,
  description: '電子部品',
  lengthCm: null,
  widthCm: null,
  heightCm: null,
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  departureDate: '2026-09-01',
  arrivalDeadline: '2026-09-30',
  hazardousClass: null,
  unNumber: null,
  properShippingName: null,
  minCelsius: null,
  maxCelsius: null,
}

/**
 * 1 位の候補。**到着がいちばん遅く、費用もいちばん高い。**
 *
 * 自然な並び（到着順・費用順）と推奨順をわざと食い違わせている。同じ並びにすると、
 * 画面が並べ替えを足しても緑のままになり、「画面は並べ替えない」（ADR-018）ことを
 * 誰も確かめていない状態になる。
 */
const DIRECT = {
  rank: 1,
  direct: true,
  voyageNumbers: ['V0100'],
  departureTime: '2026-09-01T00:00:00Z',
  arrivalTime: '2026-09-25T03:00:00Z',
  transitDays: 24,
  transshipmentCount: 0,
  transitPorts: [],
  estimatedCost: 1520000,
  legs: [
    {
      voyageNumber: 'V0100',
      vesselName: 'Pacific Star',
      carrierName: 'Nippon Express',
      fromUnLocode: 'JPTYO',
      fromName: 'Tokyo',
      toUnLocode: 'USLAX',
      toName: 'Los Angeles',
      departureTime: '2026-09-01T00:00:00Z',
      arrivalTime: '2026-09-15T03:00:00Z',
    },
  ],
}

const VIA_SHANGHAI = {
  rank: 2,
  direct: false,
  voyageNumbers: ['V0201', 'V0202'],
  departureTime: '2026-09-02T01:00:00Z',
  arrivalTime: '2026-09-18T00:00:00Z',
  transitDays: 16,
  transshipmentCount: 1,
  transitPorts: [{ unLocode: 'CNSHA', name: 'Shanghai', layoverMinutes: 38 * 60 }],
  estimatedCost: 1060000,
  legs: [
    {
      voyageNumber: 'V0201',
      vesselName: 'East Wind',
      carrierName: 'Ocean Line',
      fromUnLocode: 'JPTYO',
      fromName: 'Tokyo',
      toUnLocode: 'CNSHA',
      toName: 'Shanghai',
      departureTime: '2026-09-02T01:00:00Z',
      arrivalTime: '2026-09-04T01:00:00Z',
    },
    {
      voyageNumber: 'V0202',
      vesselName: 'West Wind',
      carrierName: 'Ocean Line',
      fromUnLocode: 'CNSHA',
      fromName: 'Shanghai',
      toUnLocode: 'USLAX',
      toName: 'Los Angeles',
      departureTime: '2026-09-05T15:00:00Z',
      arrivalTime: '2026-09-18T00:00:00Z',
    },
  ],
}

const APPLIED = {
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  arrivalDeadline: '2026-09-30T14:59:59Z',
  cargoType: 'GENERAL',
  maxTransshipments: 2,
}

function givenCandidates(candidates: unknown[], applied = APPLIED) {
  server.use(
    http.get(API_PATHS.routes, () =>
      HttpResponse.json({
        candidates,
        totalCount: candidates.length,
        appliedCriteria: applied,
      }),
    ),
  )
}

function renderPage(search = '') {
  loginAs(['ROLE_ROUTING'])
  return renderWithProviders(
    <RouteDesignPage />,
    [`/routing/design/BKG-2026000001${search}`],
    undefined,
    { path: '/routing/design/:bookingId' },
  )
}

describe('経路設計（経路候補の一覧）', () => {
  beforeEach(() => {
    server.use(
      http.get(`${API_PATHS.bookings}/BKG-2026000001`, () => HttpResponse.json(BOOKING)),
    )
    givenCandidates([DIRECT, VIA_SHANGHAI])
  })

  it('予約の条件を引き継いだ状態で開く（空のフォームを出さない）', async () => {
    renderPage()

    expect(await screen.findByDisplayValue('2026-09-30')).toBeInTheDocument()
    expect(screen.getByText(/Tokyo/)).toBeInTheDocument()
    expect(screen.getByText(/Los Angeles/)).toBeInTheDocument()
    expect(screen.getByText(/一般貨物/)).toBeInTheDocument()
  })

  it('候補をサーバが返した推奨順のまま並べる', async () => {
    renderPage()

    const rows = await screen.findAllByRole('row')
    const body = rows.slice(1)
    expect(within(body[0]).getByText('V0100')).toBeInTheDocument()
    expect(within(body[1]).getByText(/V0201/)).toBeInTheDocument()
  })

  it('直行便であることが分かる', async () => {
    renderPage()

    const rows = await screen.findAllByRole('row')
    expect(within(rows[1]).getByText('直行')).toBeInTheDocument()
  })

  it('港は名前で示し、UN/LOCODE は併記する', async () => {
    renderPage()

    expect(await screen.findByText(/Shanghai/)).toBeInTheDocument()
    expect(screen.getByText(/CNSHA/)).toBeInTheDocument()
  })

  it('日時は業務タイムゾーンで表示する', async () => {
    renderPage()

    // 2026-09-25T03:00Z = 日本時間 09-25 12:00
    expect(await screen.findByText(/2026-09-25 12:00/)).toBeInTheDocument()
  })

  it('到着が遅くても費用が高くても、サーバの順位のまま並べる', async () => {
    renderPage()

    const rows = (await screen.findAllByRole('row')).slice(1)
    // 1 行目は到着がいちばん遅い直行便。到着順・費用順に並べ替えていない
    expect(within(rows[0]).getByText('V0100')).toBeInTheDocument()
    expect(within(rows[0]).getByText(/2026-09-25 12:00/)).toBeInTheDocument()
  })

  it('費用は概算であることを画面に書く', async () => {
    renderPage()

    // 表の見出しだけでなく、注記として「概算です」と書いてあることを見る
    expect(await screen.findByText(/正式な料金は精算時に確定します/)).toBeInTheDocument()
    expect(screen.getAllByText(/概算/).length).toBeGreaterThan(0)
  })

  it('使えない機能の案内を残さない（IT5 で確定が使えるようになった）', async () => {
    renderPage()

    await screen.findAllByRole('row')
    // 使えるようになった機能に「次のリリースで」と書いたままにすると、
    // 経路設計者は使える操作を探さなくなる
    expect(screen.queryByText(/次のリリースで使えるようになります/)).not.toBeInTheDocument()
    // 「イテレーション」は利用者に通じない
    expect(screen.queryByText(/イテレーション/)).not.toBeInTheDocument()
  })

  describe('候補を選んで確定する（US09 / US11）', () => {
    it('押した瞬間には確定せず、確認を挟む', async () => {
      let assigned = false
      server.use(
        http.put(`${API_PATHS.bookings}/:bookingId/route`, () => {
          assigned = true
          return HttpResponse.json(BOOKING)
        }),
      )
      renderPage()

      await screen.findAllByRole('row')
      await userEvent.click(screen.getAllByRole('button', { name: 'この経路を選ぶ' })[0])

      // 取り消す手段の無い操作を、一覧の行から直接起こさない
      expect(await screen.findByText('この経路で確定しますか')).toBeInTheDocument()
      expect(assigned).toBe(false)
      // 確定すると何が起こるかを先に伝える
      expect(screen.getByText(/予約の状態が「経路提案中」になります/)).toBeInTheDocument()
    })

    it('確定すると選んだ区間を送り、予約詳細へ移る', async () => {
      let sent: unknown = null
      server.use(
        http.put(`${API_PATHS.bookings}/:bookingId/route`, async ({ request }) => {
          sent = await request.json()
          return HttpResponse.json(BOOKING)
        }),
      )
      renderPage()

      await screen.findAllByRole('row')
      await userEvent.click(screen.getAllByRole('button', { name: 'この経路を選ぶ' })[0])
      await userEvent.click(screen.getByRole('button', { name: 'この経路で確定する' }))

      await waitFor(() => expect(sent).not.toBeNull())
      // 候補の中身を丸ごと送る（候補 ID では参照しない。サーバは候補を保存していない）。
      // **項目を拾って比べない。**拾うと、maxTransshipments を落としても時刻を取り違えても
      // 緑のままになる（IT5 レビュー 高 11）
      expect(sent).toEqual({
        legs: [
          {
            voyageNumber: 'V0100',
            loadUnLocode: 'JPTYO',
            unloadUnLocode: 'USLAX',
            loadTime: DIRECT.legs[0].departureTime,
            unloadTime: DIRECT.legs[0].arrivalTime,
          },
        ],
        // 候補を出したときの条件で再検証させる。落とすと、緩めた条件で選んだ経路が
        // 「候補に無い」と判定される
        maxTransshipments: 2,
      })
      // 確定できたことは予約詳細で分かる。遷移まで確かめる（MemoryRouter なので
      // この画面が消えることで見る。遷移を消すと候補一覧が残り続けて赤になる）
      await waitFor(() =>
        expect(screen.queryByRole('heading', { name: '経路設計' })).not.toBeInTheDocument(),
      )
    })

    it('もう成立しない経路なら、その理由を出して選び直させる', async () => {
      server.use(
        http.put(`${API_PATHS.bookings}/:bookingId/route`, () =>
          HttpResponse.json(
            { message: '選んだ経路はもう使えません。経路をもう一度探してください' },
            { status: 409 },
          ),
        ),
      )
      renderPage()

      await screen.findAllByRole('row')
      await userEvent.click(screen.getAllByRole('button', { name: 'この経路を選ぶ' })[0])
      await userEvent.click(screen.getByRole('button', { name: 'この経路で確定する' }))

      // 次の行動は「もう一度探す」であり、入力の修正ではない
      expect(await screen.findByRole('alert')).toHaveTextContent('もう一度探してください')
    })

    it('選び直すと確認を閉じる', async () => {
      renderPage()

      await screen.findAllByRole('row')
      await userEvent.click(screen.getAllByRole('button', { name: 'この経路を選ぶ' })[0])
      await screen.findByText('この経路で確定しますか')

      await userEvent.click(screen.getByRole('button', { name: '選び直す' }))

      expect(screen.queryByText('この経路で確定しますか')).not.toBeInTheDocument()
    })
  })

  it('候補の航海から航海詳細へ行ける', async () => {
    renderPage()

    const link = await screen.findByRole('link', { name: 'V0100' })
    // 戻り先を持たせるため、リンクは航海番号だけでは終わらない（残作業 4）
    expect(link.getAttribute('href')).toContain('/routing/voyages/V0100')
  })

  describe('候補が 1 件も無かったとき', () => {
    beforeEach(() => {
      givenCandidates([], { ...APPLIED, cargoType: 'HAZARDOUS' })
    })

    it('何で絞ったかを示す', async () => {
      renderPage()

      expect(await screen.findByText(/見つかりませんでした/)).toBeInTheDocument()
      expect(screen.getByText(/危険物/)).toBeInTheDocument()
      // 貨物種別が効いていることに気づけないと、期限だけを緩め続ける
      expect(screen.getByText(/運べる船が限られます/)).toBeInTheDocument()
    })

    it('条件を緩める操作を置く（該当なしで終わらせない）', async () => {
      renderPage()

      expect(await screen.findByRole('button', { name: /到着期限を 1 週間延ばす/ })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /積み替えを 3 回まで許す/ })).toBeInTheDocument()
    })

    it('積み替えを緩めると、その条件で算出し直す', async () => {
      const requested: string[] = []
      server.use(
        http.get(API_PATHS.routes, ({ request }) => {
          requested.push(new URL(request.url).searchParams.get('maxTransshipments') ?? '')
          return HttpResponse.json({ candidates: [], totalCount: 0, appliedCriteria: APPLIED })
        }),
      )
      renderPage()

      await userEvent.click(await screen.findByRole('button', { name: /積み替えを 3 回まで許す/ }))

      await waitFor(() => expect(requested).toContain('3'))
    })

    it('航海スケジュールへの逃げ道を置く（そもそも便が無い可能性がある）', async () => {
      renderPage()

      expect(await screen.findByRole('link', { name: /航海スケジュールを見る/ })).toHaveAttribute(
        'href',
        '/routing/voyages',
      )
    })
  })

  it('期限は日付のまま送る（日時に変換しない）', async () => {
    let sentDeadline: string | null = null
    server.use(
      http.get(API_PATHS.routes, ({ request }) => {
        sentDeadline = new URL(request.url).searchParams.get('deadline')
        return HttpResponse.json({ candidates: [], totalCount: 0, appliedCriteria: APPLIED })
      }),
    )
    renderPage()

    await waitFor(() => expect(sentDeadline).toBe('2026-09-30'))
  })

  describe('到着期限を予約から変えたとき', () => {
    /**
     * 到着期限は荷主との約束であり、こちらが動かせる数字ではない。
     *
     * 延ばした条件で出た候補は、他の候補と見分けがつかない。画面に何も残らないと、
     * 経路設計者は悪気なく延長後の候補で話を進め、荷主が「9 月 30 日と言ったはずだ」となる。
     */
    it('予約の期限と、いま探している期限の両方を残す', async () => {
      renderPage()
      await screen.findAllByRole('row')

      await userEvent.clear(screen.getByLabelText('到着期限'))
      await userEvent.type(screen.getByLabelText('到着期限'), '2026-10-07')

      expect(await screen.findByText(/この予約の到着期限は/)).toBeInTheDocument()
      expect(screen.getByText(/荷主の合意が要ります/)).toBeInTheDocument()
    })

    it('予約の期限に戻せる', async () => {
      renderPage()
      await screen.findAllByRole('row')

      await userEvent.clear(screen.getByLabelText('到着期限'))
      await userEvent.type(screen.getByLabelText('到着期限'), '2026-10-07')
      await userEvent.click(await screen.findByRole('button', { name: '予約の期限に戻す' }))

      expect(screen.getByLabelText('到着期限')).toHaveValue('2026-09-30')
      expect(screen.queryByText(/荷主の合意が要ります/)).not.toBeInTheDocument()
    })
  })

  /**
   * 期限が空のまま問い合わせると 400 になり、画面には「算出できませんでした」だけが出る。
   * 経路設計者は何もしていないのに失敗を見ることになる。
   */
  it('到着期限を消したら、探しに行かず入力を促す', async () => {
    let asked = false
    server.use(
      http.get(API_PATHS.routes, () => {
        asked = true
        return HttpResponse.json({ candidates: [], totalCount: 0, appliedCriteria: APPLIED })
      }),
    )
    renderPage()
    await screen.findByDisplayValue('2026-09-30')
    asked = false

    await userEvent.clear(screen.getByLabelText('到着期限'))

    expect(await screen.findByText('到着期限を入力してください。')).toBeInTheDocument()
    expect(asked).toBe(false)
  })

  /**
   * 「経路が無い」と「港の指定が誤り」は別のことである。
   *
   * 同じ文言にすると、経路設計者は通信のせいだと思って何度も開き直す。
   */
  it('サーバが返した理由をそのまま見せる', async () => {
    server.use(
      http.get(API_PATHS.routes, () =>
        HttpResponse.json({ message: '出発地が見つかりません' }, { status: 400 }),
      ),
    )
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('出発地が見つかりません')
  })

  /** 朝の仕事は 1 件ではなく、待ち行列を上から片づけること。 */
  it('経路設計待ちの一覧に戻れる', async () => {
    renderPage()

    expect(await screen.findByRole('link', { name: '経路設計待ちの一覧に戻る' })).toHaveAttribute(
      'href',
      '/booking?routingStatus=ROUTING_REQUESTED',
    )
  })

  it('順位が到着の早さではないことを画面に書く', async () => {
    renderPage()

    expect(
      await screen.findByText(/直行便を最優先に並べています。到着の早さだけで並べているわけではありません/),
    ).toBeInTheDocument()
  })

  it('候補行に船名と運送会社を出す', async () => {
    renderPage()

    // 航海番号だけでは、経路設計者はどの船・どの会社かを別画面で調べることになる
    expect(await screen.findByText('Pacific Star')).toBeInTheDocument()
    expect(screen.getByText('/ Nippon Express')).toBeInTheDocument()
    expect(screen.getByText('East Wind')).toBeInTheDocument()
  })

  it('積み替え港での待ち時間を経路の中に出す', async () => {
    renderPage()

    // 所要日数の合計だけでは、どこでどれだけ止まるのかが分からない
    expect(
      await screen.findByText(/Shanghai \/ CNSHA・待ち 1 日 14 時間/),
    ).toBeInTheDocument()
  })

  describe('条件を URL に残す（残作業 3 / US10）', () => {
    it('URL の条件で初めから探索する', async () => {
      renderPage('?deadline=2026-10-15&maxTransshipments=3')

      // 再読み込みしても、航海詳細から戻っても、同じ条件で開き直せる
      expect(await screen.findByDisplayValue('2026-10-15')).toBeInTheDocument()
      expect(screen.getByLabelText(/積み替え/)).toHaveValue('3')
    })

    it('航海のリンクは条件ごと戻り先を渡す', async () => {
      renderPage('?deadline=2026-10-15')

      const link = await screen.findByRole('link', { name: 'V0100' })
      expect(link.getAttribute('href')).toContain(
        encodeURIComponent('/routing/design/BKG-2026000001?deadline=2026-10-15'),
      )
    })
  })

  describe('出発希望日を条件に入れる（残作業 5）', () => {
    it('予約の出発希望日を初期値にして送る', async () => {
      const sent: string[] = []
      server.use(
        http.get(API_PATHS.routes, ({ request }) => {
          sent.push(new URL(request.url).searchParams.get('earliestDeparture') ?? '')
          return HttpResponse.json({
            candidates: [DIRECT],
            totalCount: 1,
            appliedCriteria: APPLIED,
          })
        }),
      )
      renderPage()

      // 荷主が「この日以降でないと倉庫に入らない」と言っているのに、それより前に出る便を
      // 候補に出すと、押さえても積むものがない
      await waitFor(() => expect(sent).toContain('2026-09-01'))
      expect(await screen.findByDisplayValue('2026-09-01')).toBeInTheDocument()
    })

    it('URL で指定した出発希望日が予約の値より優先される', async () => {
      const sent: string[] = []
      server.use(
        http.get(API_PATHS.routes, ({ request }) => {
          sent.push(new URL(request.url).searchParams.get('earliestDeparture') ?? '')
          return HttpResponse.json({
            candidates: [DIRECT],
            totalCount: 1,
            appliedCriteria: APPLIED,
          })
        }),
      )
      renderPage('?earliestDeparture=2026-09-20')

      await waitFor(() => expect(sent).toContain('2026-09-20'))
    })
  })

  describe('見つからないとき営業へ戻す（US10・ADR-020 決定 7）', () => {
    function givenNoCandidates() {
      server.use(
        http.get(API_PATHS.routes, () =>
          HttpResponse.json({ candidates: [], totalCount: 0, appliedCriteria: APPLIED }),
        ),
      )
    }

    it('条件協議を依頼できる', async () => {
      givenNoCandidates()
      let requested = false
      server.use(
        http.post(`${API_PATHS.bookings}/:bookingId/consultation-request`, () => {
          requested = true
          return HttpResponse.json({ ...BOOKING, routingStatus: 'CONSULTATION_REQUESTED' })
        }),
      )
      renderPage()

      // 「見つかりませんでした」で終わらせると、この画面の中で行き止まりになり、
      // 荷主との条件交渉が始まらない
      await userEvent.click(await screen.findByRole('button', { name: '条件協議を依頼する' }))

      await waitFor(() => expect(requested).toBe(true))
    })

    it('すでに営業へ戻していれば、その旨を示して二重には送らせない', async () => {
      givenNoCandidates()
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json({ ...BOOKING, routingStatus: 'CONSULTATION_REQUESTED' }),
        ),
      )
      renderPage()

      expect(await screen.findByText(/この予約は営業へ戻しています/)).toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: '条件協議を依頼する' }),
      ).not.toBeInTheDocument()
    })
  })

  describe('確定できない場面（IT5 レビュー 高 3・高 10）', () => {
    it('予約の条件から動かして探している間は、確定させない', async () => {
      renderPage('?deadline=2027-01-31')

      await screen.findAllByRole('row')
      // 確定時の再検証は予約が持つ条件で行うため、緩めた条件で選んだ経路は必ず断られる。
      // しかも理由は「航海スケジュールが変わった」に見え、航海マスタを疑って探し回る
      expect(await screen.findByRole('alert')).toHaveTextContent('荷主の合意が要る')
      expect(screen.queryByRole('button', { name: 'この経路を選ぶ' })).not.toBeInTheDocument()
    })

    it('営業へ差し戻し中の予約では、確定させない', async () => {
      server.use(
        http.get(`${API_PATHS.bookings}/:bookingId`, () =>
          HttpResponse.json({ ...BOOKING, routingStatus: 'CONSULTATION_REQUESTED' }),
        ),
      )
      renderPage()

      await screen.findAllByRole('row')
      // サーバも 409 で断る。押せるようにすると、実物でだけ断られる
      expect(screen.queryByRole('button', { name: 'この経路を選ぶ' })).not.toBeInTheDocument()
      expect(await screen.findByRole('alert')).toHaveTextContent('営業へ戻しています')
    })

    it('条件を変えたら、選んでいた候補は解除する', async () => {
      renderPage()

      await screen.findAllByRole('row')
      await userEvent.click(screen.getAllByRole('button', { name: 'この経路を選ぶ' })[0])
      await screen.findByText('この経路で確定しますか')

      // 候補は取り直されるのに選んだ候補が古いまま残ると、画面に出ていないものを確定できる
      await userEvent.selectOptions(screen.getByLabelText('積み替えの上限'), '3')

      await waitFor(() =>
        expect(screen.queryByText('この経路で確定しますか')).not.toBeInTheDocument(),
      )
    })
  })

  describe('期限を超える経路（US28-6）', () => {
    /**
     * <strong>超える分を出してから進む。</strong>
     *
     * <p>そのまま予約詳細へ遷移すると、超過に気づかないまま次の作業へ移る
     * ——荷主に伝えるのは営業であり、<strong>ここで気づかなければ誰も伝えない</strong>。
     */
    it('期限を超えるときは、何日超えるかを出して遷移を止める', async () => {
      server.use(
        http.put(`${API_PATHS.bookings}/:bookingId/route`, () =>
          HttpResponse.json({ ...BOOKING, daysBeyondDeadline: 5 }),
        ),
      )
      renderPage()

      await screen.findAllByRole('row')
      await userEvent.click(screen.getAllByRole('button', { name: 'この経路を選ぶ' })[0])
      await userEvent.click(screen.getByRole('button', { name: 'この経路で確定する' }))

      const alert = await screen.findByRole('alert')
      expect(alert, '何日超えるかが出ていない。荷主は次の手を決められない')
        .toHaveTextContent('5 日超えます')
      // **通知が代替であることを言う**（[ADR-026] 決定 7）
      expect(alert).toHaveTextContent('荷主へは自動で通知されません')
      expect(
        screen.getByRole('button', { name: /確認しました/ }),
        '読んだことを示す手段が無い。読まずに閉じられる',
      ).toBeInTheDocument()
    })

    /** 期限内なら止めない。**毎回止めると、読まずに押す癖がつく**。 */
    it('期限内なら、確定後にそのまま予約詳細へ進む', async () => {
      server.use(
        http.put(`${API_PATHS.bookings}/:bookingId/route`, () =>
          HttpResponse.json({ ...BOOKING, daysBeyondDeadline: null }),
        ),
      )
      renderPage()

      await screen.findAllByRole('row')
      await userEvent.click(screen.getAllByRole('button', { name: 'この経路を選ぶ' })[0])
      await userEvent.click(screen.getByRole('button', { name: 'この経路で確定する' }))

      await waitFor(() =>
        expect(screen.queryByText(/超えます/)).not.toBeInTheDocument(),
      )
    })
  })
})
