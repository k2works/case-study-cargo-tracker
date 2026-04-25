import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect } from 'vitest'
import { LoginForm } from './LoginForm'

function renderLoginForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <LoginForm />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('LoginForm', () => {
  it('ユーザー名とパスワードの入力欄が表示される', () => {
    renderLoginForm()
    expect(screen.getByLabelText('ユーザー名')).toBeInTheDocument()
    expect(screen.getByLabelText('パスワード')).toBeInTheDocument()
  })

  it('ログインボタンが表示される', () => {
    renderLoginForm()
    expect(screen.getByRole('button', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('デフォルト値が入力されている', () => {
    renderLoginForm()
    expect(screen.getByLabelText('ユーザー名')).toHaveValue('admin')
    expect(screen.getByLabelText('パスワード')).toHaveValue('password')
  })
})
