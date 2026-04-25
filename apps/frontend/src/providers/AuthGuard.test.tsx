import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeEach } from 'vitest'
import { AuthGuard } from './AuthGuard'
import { useAuthStore } from '../stores/authStore'

function renderWithAuth(initialEntries: string[] = ['/protected']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/login" element={<div>ログイン画面</div>} />
          <Route element={<AuthGuard />}>
            <Route path="/protected" element={<div>保護されたページ</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AuthGuard', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
    sessionStorage.clear()
  })

  it('未認証時にログイン画面にリダイレクトされる', () => {
    renderWithAuth()
    expect(screen.getByText('ログイン画面')).toBeInTheDocument()
    expect(screen.queryByText('保護されたページ')).not.toBeInTheDocument()
  })

  it('認証済み時に保護されたページが表示される', () => {
    useAuthStore.getState().setAuth('test-token', { username: 'admin', roles: ['ROLE_ADMIN'] })
    renderWithAuth()
    expect(screen.getByText('保護されたページ')).toBeInTheDocument()
    expect(screen.queryByText('ログイン画面')).not.toBeInTheDocument()
  })
})
