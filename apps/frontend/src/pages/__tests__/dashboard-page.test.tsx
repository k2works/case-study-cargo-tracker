import { screen } from '@testing-library/react'
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

  it('準備中と示し、押せないようにする', () => {
    renderAs(['ROLE_HANDLER'])

    // 押した先が存在しないと、利用者は公開トップに飛ばされて
    // 「勝手にログアウトされた」と受け取る
    expect(screen.queryByRole('link', { name: /荷役作業を記録する/ })).not.toBeInTheDocument()
    expect(screen.getByText(/準備中/)).toBeInTheDocument()
  })

  it('使える画面はリンクのままにする', () => {
    renderAs(['ROLE_SALES'])

    expect(screen.getByRole('link', { name: /荷主を登録する/ })).toBeInTheDocument()
  })

  it('下位の URL が上位のメニューに吸われて押せるようにならない', () => {
    renderAs(['ROLE_TRACKER'])

    // /booking/cancellations は準備中だが /booking は使える。前方一致で最初に
    // 当たったものを使うと、この行動だけリンクになって公開トップに飛ばされる
    expect(screen.queryByRole('link', { name: /キャンセル申請を確認する/ })).not.toBeInTheDocument()
    expect(resolveNavigationItem('/booking/cancellations')?.available).toBe(false)
  })

  it('担当の画面がすべて準備中なら、その旨を伝える', () => {
    // 経理担当者の画面（精算管理）は IT3 時点でまだ無い
    renderAs(['ROLE_ACCOUNTANT'])

    // 何も使えないことが分かれば、待ち状態として受け取れる
    expect(screen.getByText(/次のリリースで使えるようになります/)).toBeInTheDocument()
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
})
