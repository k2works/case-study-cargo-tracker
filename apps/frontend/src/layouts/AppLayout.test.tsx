import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, it, expect, beforeEach } from 'vitest'
import { AppLayout } from './AppLayout'
import { useAuthStore } from '../stores/authStore'

function renderWithRoles(roles: string[]) {
  useAuthStore.setState({
    token: 'dummy-token',
    user: { username: 'tester', roles },
  })
  return render(
    <MemoryRouter initialEntries={['/shippers']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/shippers" element={<div>Shippers Content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppLayout ロール別メニュー', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, user: null })
  })

  it('ROLE_ADMIN は全てのメニュー項目を表示する', () => {
    renderWithRoles(['ROLE_ADMIN'])
    expect(screen.getByText('荷主管理')).toBeInTheDocument()
    expect(screen.getByText('見積一覧')).toBeInTheDocument()
    expect(screen.getByText('予約管理')).toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
  })

  it('ROLE_SALES は荷主管理・見積一覧・予約管理を表示する', () => {
    renderWithRoles(['ROLE_SALES'])
    expect(screen.getByText('荷主管理')).toBeInTheDocument()
    expect(screen.getByText('見積一覧')).toBeInTheDocument()
    expect(screen.getByText('予約管理')).toBeInTheDocument()
    expect(screen.queryByText('航海スケジュール')).not.toBeInTheDocument()
  })

  it('ROLE_ROUTING は航海スケジュールのみ表示する', () => {
    renderWithRoles(['ROLE_ROUTING'])
    expect(screen.queryByText('荷主管理')).not.toBeInTheDocument()
    expect(screen.queryByText('見積一覧')).not.toBeInTheDocument()
    expect(screen.queryByText('予約管理')).not.toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
  })

  it('複数ロールを持つ場合、いずれかが該当すれば項目を表示する', () => {
    renderWithRoles(['ROLE_SALES', 'ROLE_ROUTING'])
    expect(screen.getByText('荷主管理')).toBeInTheDocument()
    expect(screen.getByText('予約管理')).toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
  })

  it('該当ロールがない場合、メニュー項目を表示しない', () => {
    renderWithRoles(['ROLE_HANDLING'])
    expect(screen.queryByText('荷主管理')).not.toBeInTheDocument()
    expect(screen.queryByText('見積一覧')).not.toBeInTheDocument()
    expect(screen.queryByText('予約管理')).not.toBeInTheDocument()
    expect(screen.queryByText('航海スケジュール')).not.toBeInTheDocument()
  })

  it('見積一覧リンクは /quotations を指す', () => {
    renderWithRoles(['ROLE_SALES'])
    const link = screen.getByText('見積一覧').closest('a')
    expect(link).toHaveAttribute('href', '/quotations')
  })
})
