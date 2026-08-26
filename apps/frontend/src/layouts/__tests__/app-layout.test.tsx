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
