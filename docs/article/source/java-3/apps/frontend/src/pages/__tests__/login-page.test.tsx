import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { useAuthStore } from '../../stores/auth-store'
import { server } from '../../test/msw/server'
import { LoginPage } from '../login-page'

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

async function submit(userId: string, password: string) {
  await userEvent.type(screen.getByLabelText('利用者 ID'), userId)
  await userEvent.type(screen.getByLabelText('パスワード'), password)
  await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))
}

const FAILURE_MESSAGE = '利用者 ID またはパスワードが正しくありません'

describe('ログイン画面', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('認証に成功するとダッシュボードへ遷移する', async () => {
    server.use(
      http.post(API_PATHS.login, () =>
        HttpResponse.json({
          token: 'jwt-token',
          userId: 'sales01',
          displayName: '山田太郎',
          roles: ['ROLE_SALES'],
        }),
      ),
    )

    renderLogin()
    await submit('sales01', 'password')

    expect(await screen.findByRole('heading', { name: 'ダッシュボード' })).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBe('jwt-token')
  })

  it.each([
    ['認証情報が誤っている', '利用者 ID またはパスワードが正しくありません'],
    ['アカウントがロックされている', 'アカウントがロックされています'],
    ['アカウントが無効化されている', 'このアカウントは無効です'],
  ])('%s 場合もサーバーの文言に関わらず同一のメッセージを表示する', async (_case, serverMessage) => {
    // 失敗理由ごとに表示を変えると、攻撃者に「その利用者 ID は存在する」と教えてしまう（US31）
    server.use(
      http.post(API_PATHS.login, () =>
        HttpResponse.json({ message: serverMessage }, { status: 401 }),
      ),
    )

    renderLogin()
    await submit('sales01', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent(FAILURE_MESSAGE)
    expect(screen.queryByText(/ロック/)).not.toBeInTheDocument()
    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
  })

  it.each([
    ['サーバー側の異常', 500],
    ['ゲートウェイに繋がらない', 502],
    ['サービスが起きていない', 503],
  ])('%s ときは認証の失敗と区別して伝える', async (_case, status) => {
    // 繋がらないだけなのに「ID かパスワードが違う」と言われると、
    // 利用者は正しい情報を何度も打ち直すことになる
    server.use(http.post(API_PATHS.login, () => new HttpResponse(null, { status })))

    renderLogin()
    await submit('sales01', 'password')

    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent(FAILURE_MESSAGE)
    expect(alert).toHaveTextContent(/接続できません|時間をおいて/)
  })

  it('ネットワークに届かないときも認証の失敗と区別して伝える', async () => {
    server.use(http.post(API_PATHS.login, () => HttpResponse.error()))

    renderLogin()
    await submit('sales01', 'password')

    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent(FAILURE_MESSAGE)
  })

  it('認証不要の追跡照会への導線を置く', () => {
    renderLogin()

    expect(screen.getByRole('link', { name: /追跡照会/ })).toBeInTheDocument()
  })
})
