import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import type { Role } from '../../types/role'
import { DashboardPage } from '../dashboard-page'

function renderAs(roles: Role[]) {
  useAuthStore.getState().login({
    token: 't',
    userId: 'u01',
    displayName: 'テスト利用者',
    roles,
  })

  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
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
