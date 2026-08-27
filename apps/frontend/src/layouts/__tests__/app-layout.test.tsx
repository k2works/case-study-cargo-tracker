import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import type { Role } from '../../types/role'
import { loginAs, renderWithProviders } from '../../test/render'
import { AppLayout } from '../app-layout'
import { NAVIGATION } from '../../config/navigation'

function renderAs(roles: Role[]) {
  loginAs(roles)
  return renderWithProviders(<AppLayout />)
}

describe('いま開いている画面の示し方', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  /**
   * **選択状態は 1 つだけ**（IT12 のキャプチャで気づいた）。
   *
   * <p>`NavLink` の既定は前方一致であり、`/booking/estimates/new` を開くと
   * 「見積管理」と「貨物予約」が**同時に**選択状態になっていた——どちらが自分の
   * 居場所か分からなくなる。
   */
  it('下位の画面を開いても、選択状態になる項目は 1 つだけ', () => {
    loginAs(['ROLE_SALES'])
    renderWithProviders(<AppLayout />, ['/booking/estimates/new'])

    const selected = screen
      .getAllByRole('link')
      .filter((link) => link.className.includes('bg-blue-50'))
      .map((link) => link.textContent)

    expect(selected, '選択状態の項目が 1 つに定まっていない').toEqual(['見積管理'])
  })

  it('上位の画面を開いたら、上位の項目が選択状態になる', () => {
    loginAs(['ROLE_SALES'])
    renderWithProviders(<AppLayout />, ['/booking'])

    const selected = screen
      .getAllByRole('link')
      .filter((link) => link.className.includes('bg-blue-50'))
      .map((link) => link.textContent)

    expect(selected).toEqual(['貨物予約'])
  })
})

describe('共通レイアウトのナビゲーション', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('ダッシュボードとログアウトは全ロールに出す', () => {
    renderAs(['ROLE_HANDLER'])

    expect(screen.getByRole('link', { name: 'ダッシュボード' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'ログアウト' })).toBeInTheDocument()
  })

  it('営業担当者には荷主管理を出す', () => {
    renderAs(['ROLE_SALES'])

    expect(screen.getByRole('link', { name: '荷主管理' })).toBeInTheDocument()
  })

  it('営業担当者でないなら荷主管理は出さない', () => {
    renderAs(['ROLE_ACCOUNTANT'])

    expect(screen.queryByText('荷主管理')).not.toBeInTheDocument()
    // 精算管理はまだ使えないが、担当のメニューとしては見える
    expect(screen.getByText('精算管理')).toBeInTheDocument()
  })

  it('複数ロールを持つ利用者には両方のメニューを出す', () => {
    renderAs(['ROLE_SALES', 'ROLE_TRACKER'])

    expect(screen.getByRole('link', { name: '荷主管理' })).toBeInTheDocument()
    expect(screen.getByText('キャンセル承認')).toBeInTheDocument()
  })

  it('誰が使っているかを画面上で分かるようにする', () => {
    renderAs(['ROLE_ROUTING'])

    expect(screen.getByText(/テスト利用者/)).toBeInTheDocument()
    // ROLE_ROUTING ではなく業務上の呼び名で示す
    expect(screen.getByText(/経路設計者/)).toBeInTheDocument()
  })

  it('荷主には自分の貨物メニューを出す', () => {
    renderAs(['ROLE_SHIPPER'])

    expect(screen.getByRole('link', { name: '自分の貨物' })).toHaveAttribute(
      'href',
      '/shipper/tracking',
    )
  })
})

describe('まだ使えない画面のメニュー', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  /**
   * **準備中の項目はもう無い**（IT12 で見積管理が最後だった）。
   *
   * <p>「準備中と示して押させない」実装は残してある——次に定義だけ先行する項目が
   * 来たときに必要になる。<strong>いま踏める道が無いので、代わりに逆側を固定する</strong>
   * ——準備中の印が付いた項目が現れたら、それはリンクにならないこと。
   */
  it('準備中の項目は無く、あればリンクにしない', () => {
    renderAs(['ROLE_SALES'])

    expect(
      NAVIGATION.filter((item) => !item.available).map((item) => item.to),
      '準備中のまま残っている項目がある。画面はあるのに navbar から到達できない',
    ).toEqual([])

    // 見積管理は IT12 で使えるようになった。**押せることを確かめる**
    expect(screen.getByRole('link', { name: '見積管理' })).toBeInTheDocument()
  })

  it('使える画面はリンクのままにする', () => {
    renderAs(['ROLE_SALES'])

    expect(screen.getByRole('link', { name: '荷主管理' })).toBeInTheDocument()
  })
})
