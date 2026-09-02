import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import { RequireAuth } from '../require-auth'

function renderAt(path: string, allowedRoles?: string[]) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<h1>ログイン</h1>} />
        <Route path="/403" element={<h1>権限がありません</h1>} />
        <Route
          path="/secret"
          element={
            <RequireAuth allowedRoles={allowedRoles as never}>
              <h1>業務画面</h1>
            </RequireAuth>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ルーティングガード', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('未認証ならログイン画面へ送る', () => {
    renderAt('/secret')

    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '業務画面' })).not.toBeInTheDocument()
  })

  it('認証済みで許可ロールを持つなら画面を表示する', () => {
    useAuthStore.getState().login({
      token: 't',
      userId: 'sales01',
      displayName: '山田太郎',
      roles: ['ROLE_SALES'],
    })

    renderAt('/secret', ['ROLE_SALES'])

    expect(screen.getByRole('heading', { name: '業務画面' })).toBeInTheDocument()
  })

  it('認証済みでも許可ロールを持たなければ 403 を表示する', () => {
    useAuthStore.getState().login({
      token: 't',
      userId: 'handler01',
      displayName: '鈴木一郎',
      roles: ['ROLE_HANDLER'],
    })

    renderAt('/secret', ['ROLE_SALES'])

    expect(screen.getByRole('heading', { name: '権限がありません' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '業務画面' })).not.toBeInTheDocument()
  })
})
