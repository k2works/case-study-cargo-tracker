import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import type { Role } from '../../types/role'
import { AppLayout } from '../app-layout'

function renderAs(roles: Role[]) {
  useAuthStore.getState().login({
    token: 't',
    userId: 'u01',
    displayName: 'テスト利用者',
    roles,
  })

  return render(
    <MemoryRouter>
      <AppLayout />
    </MemoryRouter>,
  )
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

  it('準備中と示し、押せないようにする', () => {
    renderAs(['ROLE_SALES'])

    // 押せるのにどこにも行けないメニューは、壊れていると受け取られる
    const notReady = screen.getByText('見積管理')
    expect(notReady.closest('a')).toBeNull()
    expect(screen.getAllByText('準備中').length).toBeGreaterThan(0)
  })

  it('使える画面はリンクのままにする', () => {
    renderAs(['ROLE_SALES'])

    expect(screen.getByRole('link', { name: '荷主管理' })).toBeInTheDocument()
  })
})
