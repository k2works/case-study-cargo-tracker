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

    expect(screen.queryByRole('link', { name: '荷主管理' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '精算管理' })).toBeInTheDocument()
  })

  it('複数ロールを持つ利用者には両方のメニューを出す', () => {
    renderAs(['ROLE_SALES', 'ROLE_TRACKER'])

    expect(screen.getByRole('link', { name: '荷主管理' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'キャンセル承認' })).toBeInTheDocument()
  })

  it('誰が使っているかを画面上で分かるようにする', () => {
    renderAs(['ROLE_ROUTING'])

    expect(screen.getByText(/テスト利用者/)).toBeInTheDocument()
    // ROLE_ROUTING ではなく業務上の呼び名で示す
    expect(screen.getByText(/経路設計者/)).toBeInTheDocument()
  })
})
