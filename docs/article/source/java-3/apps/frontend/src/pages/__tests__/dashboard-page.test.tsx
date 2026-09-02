import { cleanup, screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import type { Role } from '../../types/role'
import { loginAs, renderWithProviders } from '../../test/render'
import { resolveNavigationItem } from '../../config/navigation'
import { PANELS } from '../../config/dashboard-panels'
import { DashboardPage } from '../dashboard-page'

function renderAs(roles: Role[]) {
  loginAs(roles)
  return renderWithProviders(<DashboardPage />)
}

describe('ダッシュボード', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('営業担当者には営業のダッシュボードを出す', () => {
    renderAs(['ROLE_SALES'])

    expect(screen.getByRole('heading', { name: '営業ダッシュボード' })).toBeInTheDocument()
    // 件数を出すだけでは仕事が進まない。そこから対象へ行けること
    expect(screen.getByRole('link', { name: /荷主を登録する/ })).toHaveAttribute(
      'href',
      '/booking/shippers/new',
    )
  })

  it('担当が違えばその担当のダッシュボードを出す', () => {
    renderAs(['ROLE_ACCOUNTANT'])

    expect(screen.getByRole('heading', { name: '経理ダッシュボード' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '営業ダッシュボード' })).not.toBeInTheDocument()
  })

  it('荷主は自分の貨物一覧へ進める', () => {
    renderAs(['ROLE_SHIPPER'])

    expect(screen.getByRole('heading', { name: '荷主ダッシュボード' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '自分の貨物を見る' })).toHaveAttribute(
      'href',
      '/shipper/tracking',
    )
  })

  it('複数ロールを持つ利用者にはすべての担当を出す', () => {
    renderAs(['ROLE_SALES', 'ROLE_TRACKER'])

    expect(screen.getByRole('heading', { name: '営業ダッシュボード' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '追跡管理ダッシュボード' })).toBeInTheDocument()
  })
})

describe('まだ使えない画面への導線', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  /**
   * **IT11 でダッシュボードから「準備中」が無くなった。**
   *
   * 経理担当者の精算管理が最後の 1 つだった。残る準備中は見積管理
   * （`/booking/estimates`・US01・IT12）だけで、これはナビゲーションにしかない。
   *
   * したがってここで確かめるのは、**押せない行動が残っていないこと**である。
   * 押した先が存在しないと、利用者は公開トップに飛ばされて
   * 「勝手にログアウトされた」と受け取る。
   */
  it('どのロールのダッシュボードにも、押せない行動が残っていない', () => {
    for (const panel of PANELS) {
      useAuthStore.getState().logout()
      renderAs([panel.role])

      expect(
        screen.queryByText(/準備中/),
        `${panel.title} に準備中の行動が残っている`,
      ).not.toBeInTheDocument()
      cleanup()
    }
  })

  /** US15。IT7 で使えるようになった。「準備中」のままだと、そこへ行けない。 */
  it('荷役作業の記録は、荷役作業員のダッシュボードから踏める', () => {
    renderAs(['ROLE_HANDLER'])

    expect(screen.getByRole('link', { name: /荷役作業を記録する/ }))
      .toHaveAttribute('href', '/handling')
  })

  it('使える画面はリンクのままにする', () => {
    renderAs(['ROLE_SALES'])

    expect(screen.getByRole('link', { name: /荷主を登録する/ })).toBeInTheDocument()
  })

  it('下位の URL が上位のメニューに吸われない', () => {
    // /booking/estimates と /booking はどちらも使えるが、**別の画面である**。
    // 前方一致で最初に当たったものを返すと、見積管理の行動が貨物予約の項目として
    // 解決され、ロールや到達性の判定が別画面のものになる。
    //
    // IT12 で準備中の項目が無くなったため、「準備中かどうか」ではなく
    // **どの項目に解決されるか**で確かめる。**この性質は画面が増えても保ち続ける**
    expect(resolveNavigationItem('/booking/estimates')?.to).toBe('/booking/estimates')
    expect(resolveNavigationItem('/booking/estimates/new')?.to).toBe('/booking/estimates')
    expect(resolveNavigationItem('/booking')?.to).toBe('/booking')
  })

  /**
   * **どのロールにも、押せる行動が 1 つ以上ある**（ロール別到達性）。
   *
   * IT11 までは「担当の画面がすべて準備中なら、その旨を伝える」を確かめていたが、
   * **そのようなロールは無くなった**（経理担当者が最後だった）。伝える実装は残して
   * あるが、いま踏める道が無いので、代わりに**逆側**を固定する。
   *
   * 画面が増えるほど、あるロールだけ導線が抜ける形が起きやすい
   * （IT7・IT9・IT10 で 3 度踏んだ）。
   */
  it('どのロールにも、押せる行動が 1 つ以上ある', () => {
    for (const panel of PANELS) {
      useAuthStore.getState().logout()
      renderAs([panel.role])

      expect(
        screen.getAllByRole('link').length,
        `${panel.title} から行ける画面が 1 つも無い`,
      ).toBeGreaterThan(0)
      cleanup()
    }
  })
})

describe('ロール別の到達性', () => {
  /**
   * ダッシュボードに並べたリンクは、そのロールで実際に開けなければならない。
   *
   * 画面を別のロールに限定したとき、その画面へのリンクを消し忘れると、
   * 押した先で 403 になる。「画面を閉じた」だけでは仕事は止まらないが、
   * 「入口だけ残っている」と利用者は毎回そこで詰まる。
   */
  it.each(PANELS)('$title のリンクは $role で開ける', (panel) => {
    for (const action of panel.actions) {
      // 本番と同じ関数で判定する。ここで独自に書くと、検査だけが正しく判定して
      // 本番の誤りを素通りさせる
      const menu = resolveNavigationItem(action.to)

      expect(menu, `${action.to} に対応するメニューが無い`).toBeDefined()
      // roles が空のメニューは全ロール共通
      const allowed = menu!.roles.length === 0 || menu!.roles.includes(panel.role)
      expect(allowed, `${panel.role} は ${action.to} を開けない`).toBe(true)
    }
  })
})

describe('経路設計待ちの気づき（US06）', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  /**
   * 件数を出すだけでは仕事は進まない。
   *
   * メール通知の仕組みが無いため、経路設計者はこの表示で気づく。気づいたあと対象へ
   * 行けなければ、経路設計者は一覧を自分で探すことになる。
   */
  it('件数を出し、そこから対象の一覧へ行ける', async () => {
    server.use(
      http.get(API_PATHS.bookings, ({ request }) => {
        const status = new URL(request.url).searchParams.get('routingStatus')
        return HttpResponse.json({
          bookings: [],
          totalCount: status === 'ROUTING_REQUESTED' ? 3 : 0,
          limit: 100,
          truncated: false,
        })
      }),
    )
    renderAs(['ROLE_ROUTING'])

    expect(await screen.findByText(/経路設計を待っている予約が 3 件あります/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '経路設計を待っている予約を見る' })).toHaveAttribute(
      'href',
      '/booking?routingStatus=ROUTING_REQUESTED',
    )
  })

  /**
   * <strong>営業の朝の仕事が回るか</strong>（IT6 のクローズレビュー）。
   *
   * <p>経路が決まったことは営業に何も知らされない（メールの仕組みが無い）。一覧の「経路」列は
   * 通知前も通知後も「経路確定」のままなので、そこからは分けられない。予約が増えるほど
   * 通知待ちの数件は見つからなくなる。
   */
  it('営業は、通知待ち・返事待ち・番号を伝える予約へ行ける', async () => {
    server.use(
      http.get(API_PATHS.bookings, () =>
        HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false }),
      ),
    )
    renderAs(['ROLE_SALES'])

    expect(
      await screen.findByRole('link', { name: '荷主へ通知していない予約を見る' }),
    ).toHaveAttribute('href', '/booking?bookingStatus=ROUTE_PROPOSED')
    expect(
      screen.getByRole('link', { name: '荷主の返事を待っている予約を見る' }),
    ).toHaveAttribute('href', '/booking?bookingStatus=ROUTE_NOTIFIED')
    // 番号を発行するのは経路設計者、伝えるのは営業。知らされないと伝え忘れる
    expect(
      screen.getByRole('link', { name: '追跡番号を荷主へ伝える予約を見る' }),
    ).toHaveAttribute('href', '/booking?bookingStatus=TRACKING_ISSUED')
  })

  /**
   * US13-3。<strong>状態軸の到達性</strong>——確定した予約から追跡番号を発行するのは
   * 経路設計者であり、そこへ行く導線が無いと発行が始まらない。
   */
  it('経路設計者は、追跡番号の発行を待っている予約へ行ける', async () => {
    server.use(
      http.get(API_PATHS.bookings, () =>
        HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false }),
      ),
    )
    renderAs(['ROLE_ROUTING'])

    expect(
      await screen.findByRole('link', { name: '追跡番号の発行を待っている予約を見る' }),
    ).toHaveAttribute('href', '/booking?bookingStatus=CONFIRMED')
  })

  /**
   * US28・デモ項目 5。<strong>誤配は放っておくほど貨物が目的地から遠ざかる</strong>。
   *
   * <p>組み直すのは経路設計者だが、誤配を検知するのは荷役の記録であり、経路設計者には
   * 何も知らされない。件数を出すだけでは足りず、そこから対象の予約へ行けて初めて
   * 組み直しが始まる。絞り込みの値は一覧の選択肢と同じ <code>MISROUTED</code> である。
   */
  it('経路設計者は、誤配の件数からその予約の一覧へ行ける', async () => {
    server.use(
      http.get(API_PATHS.bookings, ({ request }) => {
        const status = new URL(request.url).searchParams.get('routingStatus')
        return HttpResponse.json({
          bookings: [],
          totalCount: status === 'MISROUTED' ? 2 : 0,
          limit: 100,
          truncated: false,
        })
      }),
    )
    renderAs(['ROLE_ROUTING'])

    expect(await screen.findByText(/誤配が起きている予約が 2 件あります/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '誤配が起きている予約を見る' })).toHaveAttribute(
      'href',
      '/booking?routingStatus=MISROUTED',
    )
  })

  it('誤配が無いときは件数を出さない', async () => {
    server.use(
      http.get(API_PATHS.bookings, () =>
        HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false }),
      ),
    )
    renderAs(['ROLE_ROUTING'])

    await screen.findByRole('heading', { name: '経路設計ダッシュボード' })
    expect(screen.queryByText(/誤配が起きている予約が/)).not.toBeInTheDocument()
  })

  it('待っている予約が無いときは何も出さない', async () => {
    server.use(
      http.get(API_PATHS.bookings, () =>
        HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false }),
      ),
    )
    renderAs(['ROLE_ROUTING'])

    await screen.findByRole('heading', { name: '経路設計ダッシュボード' })
    expect(screen.queryByText(/経路設計を待っている予約が/)).not.toBeInTheDocument()
  })

  describe('営業側の気づき（#553）', () => {
    it('まだ経路設計を依頼していない予約の件数と、その一覧への入口を出す', async () => {
      server.use(
        http.get(API_PATHS.bookings, ({ request }) => {
          const status = new URL(request.url).searchParams.get('routingStatus')
          return HttpResponse.json({
            bookings: [],
            totalCount: status === 'NOT_ROUTED' ? 2 : 0,
            limit: 100,
            truncated: false,
          })
        }),
      )
      renderAs(['ROLE_SALES'])

      // 引き渡し忘れは、予約が増えるほど一覧を見ても気づけなくなる
      expect(
        await screen.findByText(/まだ経路設計を依頼していない予約が 2 件あります/),
      ).toBeInTheDocument()
      // 件数だけ出しても仕事は進まない。そこから対象へ行けることが要る
      expect(
        screen.getByRole('link', { name: 'まだ依頼していない予約を見る' }),
      ).toHaveAttribute('href', '/booking?routingStatus=NOT_ROUTED')
    })

    it('経路設計者から戻ってきた予約の件数と、その一覧への入口を出す', async () => {
      server.use(
        http.get(API_PATHS.bookings, ({ request }) => {
          const status = new URL(request.url).searchParams.get('routingStatus')
          return HttpResponse.json({
            bookings: [],
            totalCount: status === 'CONSULTATION_REQUESTED' ? 1 : 0,
            limit: 100,
            truncated: false,
          })
        }),
      )
      renderAs(['ROLE_SALES'])

      // 荷主と条件を話せるのは営業だけ。気づかないと予約が止まったままになる
      expect(
        await screen.findByText(/条件の協議を求められている予約が 1 件あります/),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('link', { name: '条件の協議を求められている予約を見る' }),
      ).toHaveAttribute('href', '/booking?routingStatus=CONSULTATION_REQUESTED')
    })

    it('依頼していない予約が無いときは件数を出さない', async () => {
      server.use(
        http.get(API_PATHS.bookings, () =>
          HttpResponse.json({ bookings: [], totalCount: 0, limit: 100, truncated: false }),
        ),
      )
      renderAs(['ROLE_SALES'])

      await screen.findByRole('heading', { name: '営業ダッシュボード' })
      expect(screen.queryByText(/まだ経路設計を依頼していない予約が/)).not.toBeInTheDocument()
    })
  })

  describe('追跡管理者が自分で気づく件数', () => {
    /**
     * US29-6。**「1 日 1 回一覧を見る」は仕組みではない。**
     *
     * 忙しい日ほど抜け、保管料が発生してから荷主に指摘される。件数を出すだけでは
     * 仕事が進まないので、そこから対象一覧へ行けることまで見る。
     */
    it('留置 3 日超の件数が出て、対象一覧へ行ける', async () => {
      server.use(
        http.get(`${API_PATHS.customs}/overdue`, () => HttpResponse.json({ count: 3 })),
      )
      renderAs(['ROLE_TRACKER'])

      expect(
        await screen.findByText(/留置のまま 3 日を超えた通関申告が 3 件あります/),
      ).toBeInTheDocument()
      expect(screen.getByRole('link', { name: '通関の状態を管理する' })).toHaveAttribute(
        'href',
        '/customs',
      )
    })

    it('留置 3 日超が無いときは件数を出さない', async () => {
      server.use(
        http.get(`${API_PATHS.customs}/overdue`, () => HttpResponse.json({ count: 0 })),
      )
      renderAs(['ROLE_TRACKER'])

      await screen.findByRole('heading', { name: '追跡管理ダッシュボード' })
      expect(screen.queryByText(/留置のまま 3 日を超えた/)).not.toBeInTheDocument()
    })

    /** US30-4 の通知の代替。承認しないと貨物は行き先を失ったまま船に乗り続ける。 */
    it('承認待ちのキャンセル件数が出て、対象一覧へ行ける', async () => {
      server.use(
        http.get(API_PATHS.cancellations, () =>
          HttpResponse.json([
            {
              cancellationId: 1,
              bookingId: 'BKG-2026000005',
              reason: '荷主都合',
              status: 'REQUESTED',
              statusLabel: '承認待ち',
              requestedBy: 'sales01',
              requestedAt: '2026-08-25 09:00',
              bookingStatusAtRequest: 'IN_TRANSIT',
              bookingStatusAtRequestLabel: '輸送中',
              dischargeLocationUnLocode: null,
              dischargeLocationName: null,
              decidedBy: null,
              decidedAt: null,
              decisionReason: null,
              dischargeCandidates: [],
            },
          ]),
        ),
      )
      renderAs(['ROLE_TRACKER'])

      expect(
        await screen.findByText(/承認を待っているキャンセル申請が 1 件あります/),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('link', { name: '承認待ちのキャンセルを見る' }),
      ).toHaveAttribute('href', '/booking/cancellations')
    })
  })
})
