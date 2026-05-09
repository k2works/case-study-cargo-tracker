import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DashboardPage } from './DashboardPage'
import * as authStoreModule from '../stores/authStore'

function renderPage() {
  return render(
    <BrowserRouter>
      <DashboardPage />
    </BrowserRouter>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('ダッシュボードタイトルが表示される', () => {
    vi.spyOn(authStoreModule, 'useAuthStore').mockReturnValue(null)
    renderPage()
    expect(screen.getByRole('heading', { name: 'ダッシュボード' })).toBeInTheDocument()
  })

  it('ログインユーザー名が表示される', () => {
    vi.spyOn(authStoreModule, 'useAuthStore').mockReturnValue({
      username: 'testuser',
      roles: [],
    })
    renderPage()
    expect(screen.getByText(/ようこそ、testuser さん/)).toBeInTheDocument()
  })

  it('ROLE_ADMIN ユーザーには全メニューが表示される', () => {
    vi.spyOn(authStoreModule, 'useAuthStore').mockReturnValue({
      username: 'admin',
      roles: ['ROLE_ADMIN'],
    })
    renderPage()
    expect(screen.getByText('貨物予約')).toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
    expect(screen.getByText('経路設計担当')).toBeInTheDocument()
    expect(screen.getByText('荷役記録')).toBeInTheDocument()
    expect(screen.getByText('貨物追跡')).toBeInTheDocument()
  })

  it('ROLE_SALES ユーザーには貨物予約・荷役記録・貨物追跡が表示される', () => {
    vi.spyOn(authStoreModule, 'useAuthStore').mockReturnValue({
      username: 'sales',
      roles: ['ROLE_SALES'],
    })
    renderPage()
    expect(screen.getByText('貨物予約')).toBeInTheDocument()
    expect(screen.queryByText('航海スケジュール')).not.toBeInTheDocument()
    expect(screen.queryByText('経路設計担当')).not.toBeInTheDocument()
  })

  it('ROLE_ROUTING ユーザーには航海スケジュール・経路設計担当が表示される', () => {
    vi.spyOn(authStoreModule, 'useAuthStore').mockReturnValue({
      username: 'routing',
      roles: ['ROLE_ROUTING'],
    })
    renderPage()
    expect(screen.queryByText('貨物予約')).not.toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
    expect(screen.getByText('経路設計担当')).toBeInTheDocument()
  })
})
