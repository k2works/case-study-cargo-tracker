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
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<div>Dashboard Content</div>} />
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
    expect(screen.getByText('ダッシュボード')).toBeInTheDocument()
    expect(screen.getByText('予約')).toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
    expect(screen.getByText('荷主管理')).toBeInTheDocument()
  })

  it('ROLE_SALES はダッシュボード・予約・荷主管理を表示する', () => {
    renderWithRoles(['ROLE_SALES'])
    expect(screen.getByText('ダッシュボード')).toBeInTheDocument()
    expect(screen.getByText('予約')).toBeInTheDocument()
    expect(screen.getByText('荷主管理')).toBeInTheDocument()
    expect(screen.queryByText('航海スケジュール')).not.toBeInTheDocument()
  })

  it('ROLE_ROUTING はダッシュボード・航海スケジュールを表示する', () => {
    renderWithRoles(['ROLE_ROUTING'])
    expect(screen.getByText('ダッシュボード')).toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
    expect(screen.queryByText('予約')).not.toBeInTheDocument()
    expect(screen.queryByText('荷主管理')).not.toBeInTheDocument()
  })

  it('複数ロールを持つ場合、いずれかが該当すれば項目を表示する', () => {
    renderWithRoles(['ROLE_SALES', 'ROLE_ROUTING'])
    expect(screen.getByText('ダッシュボード')).toBeInTheDocument()
    expect(screen.getByText('予約')).toBeInTheDocument()
    expect(screen.getByText('航海スケジュール')).toBeInTheDocument()
    expect(screen.getByText('荷主管理')).toBeInTheDocument()
  })

  it('該当ロールがない場合、ダッシュボード以外は表示しない', () => {
    renderWithRoles(['ROLE_HANDLING'])
    expect(screen.queryByText('予約')).not.toBeInTheDocument()
    expect(screen.queryByText('航海スケジュール')).not.toBeInTheDocument()
    expect(screen.queryByText('荷主管理')).not.toBeInTheDocument()
  })

  it('ヘッダータイトルが国際貨物輸送管理である', () => {
    renderWithRoles(['ROLE_ADMIN'])
    expect(screen.getByText('国際貨物輸送管理')).toBeInTheDocument()
  })
})
