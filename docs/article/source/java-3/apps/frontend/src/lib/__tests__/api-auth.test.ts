import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { installApiAuth } from '../../features/auth/install-api-auth'
import { useAuthStore } from '../../stores/auth-store'
import { server } from '../../test/msw/server'
import { apiClient } from '../api-client'

describe('API 呼び出しの認証', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
    installApiAuth()
  })

  it('ログイン済みならトークンを添えて送る', async () => {
    let sent: string | null = null
    server.use(
      http.get(API_PATHS.shippers, ({ request }) => {
        sent = request.headers.get('Authorization')
        return HttpResponse.json([])
      }),
    )
    useAuthStore.getState().login({
      token: 'jwt-token',
      userId: 'sales01',
      displayName: '山田太郎',
      roles: ['ROLE_SALES'],
    })

    await apiClient.get(API_PATHS.shippers)

    // 添えなければ Gateway は必ず 401 を返す。画面は何も表示できない
    expect(sent).toBe('Bearer jwt-token')
  })

  it('未ログインならトークンを添えない', async () => {
    let sent: string | null = 'not-called'
    server.use(
      http.get(API_PATHS.shippers, ({ request }) => {
        sent = request.headers.get('Authorization')
        return HttpResponse.json([])
      }),
    )

    await apiClient.get(API_PATHS.shippers)

    expect(sent).toBeNull()
  })

  it('401 が返ったら認証状態を捨てる', async () => {
    server.use(http.get(API_PATHS.shippers, () => new HttpResponse(null, { status: 401 })))
    useAuthStore.getState().login({
      token: 'expired-token',
      userId: 'sales01',
      displayName: '山田太郎',
      roles: ['ROLE_SALES'],
    })

    await expect(apiClient.get(API_PATHS.shippers)).rejects.toThrow()

    // 期限切れのトークンを持ったままだと、画面は開くのに何も取得できない状態が続く
    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
  })
})
