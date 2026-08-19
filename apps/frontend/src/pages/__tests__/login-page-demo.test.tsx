import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { API_PATHS } from '../../config/api'
import { demoLoginOf } from '../../config/demo-login'
import { useAuthStore } from '../../stores/auth-store'
import { server } from '../../test/msw/server'

// 有効化した状態を再現する。既定（無効）は login-page.test.tsx が担う
vi.mock('../../config/demo-login', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../config/demo-login')>()
  return { ...actual, DEMO_LOGIN: actual.demoLoginOf('true') }
})

const { LoginPage } = await import('../login-page')

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<h1>ダッシュボード</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('開発環境のログイン画面', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('利用者 ID とパスワードが入力済みになっている', () => {
    renderLogin()

    expect(screen.getByLabelText('利用者 ID')).toHaveValue('sales01')
    expect(screen.getByLabelText('パスワード')).toHaveValue('password')
  })

  it('開発環境である旨をはっきり示す', () => {
    renderLogin()

    // 事前入力されていることを隠すと、本番同様の画面だと思い込まれる
    expect(screen.getByText(/開発環境/)).toBeInTheDocument()
  })

  it('担当ごとの利用者と共通パスワードを一覧する', () => {
    renderLogin()

    expect(screen.getByText(/パスワードは共通/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'tracker01' })).toBeInTheDocument()
    expect(screen.getByText(/追跡管理者/)).toBeInTheDocument()
    expect(screen.getByText(/無効化されたアカウント/)).toBeInTheDocument()
  })

  it('一覧の利用者を選ぶと入力欄に反映される', async () => {
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: 'handler01' }))

    expect(screen.getByLabelText('利用者 ID')).toHaveValue('handler01')
    // パスワードは共通なので入れ直さなくてよい
    expect(screen.getByLabelText('パスワード')).toHaveValue('password')
  })

  it('選んでそのままログインできる', async () => {
    server.use(
      http.post(API_PATHS.login, () =>
        HttpResponse.json({
          token: 'jwt-token',
          userId: 'tracker01',
          displayName: '佐藤花子',
          roles: ['ROLE_TRACKER'],
        }),
      ),
    )

    renderLogin()
    await userEvent.click(screen.getByRole('button', { name: 'tracker01' }))
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByRole('heading', { name: 'ダッシュボード' })).toBeInTheDocument()
  })
})

describe('本番相当のログイン画面', () => {
  it('無効なら認証情報も一覧も出さない', () => {
    // 有効化を書き忘れたら安全側に倒れることを、無効側からも固定する
    const demo = demoLoginOf(undefined)

    expect(demo.userId).toBe('')
    expect(demo.accounts).toHaveLength(0)
  })
})
