import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import type { Role } from '../../types/role'
import { loginAs, renderWithProviders } from '../../test/render'
import { NAVIGATION } from '../../config/navigation'
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

  it('担当の画面がすべて準備中なら、その旨を伝える', () => {
    renderAs(['ROLE_ROUTING'])

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
      // 最も具体的なメニューで判断する。前方一致の最初に当たったものを使うと、
      // /booking/cancellations が /booking のメニューに吸われて誤判定する
      const menu = NAVIGATION.filter((item) => action.to.startsWith(item.to) && item.to !== '/')
        .sort((a, b) => b.to.length - a.to.length)[0]

      expect(menu, `${action.to} に対応するメニューが無い`).toBeDefined()
      // roles が空のメニューは全ロール共通
      const allowed = menu!.roles.length === 0 || menu!.roles.includes(panel.role)
      expect(allowed, `${panel.role} は ${action.to} を開けない`).toBe(true)
    }
  })
})
