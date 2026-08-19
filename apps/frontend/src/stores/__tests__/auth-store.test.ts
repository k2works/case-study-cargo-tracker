import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../auth-store'

describe('認証ストア', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('初期状態では未認証である', () => {
    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('ログインするとトークンと利用者情報を保持する', () => {
    useAuthStore.getState().login({
      token: 'jwt-token',
      userId: 'sales01',
      displayName: '山田太郎',
      roles: ['ROLE_SALES'],
    })

    const state = useAuthStore.getState()
    expect(state.isAuthenticated()).toBe(true)
    expect(state.token).toBe('jwt-token')
    expect(state.user?.displayName).toBe('山田太郎')
  })

  it('ログアウトすると保持していた情報を残さない', () => {
    useAuthStore.getState().login({
      token: 'jwt-token',
      userId: 'sales01',
      displayName: '山田太郎',
      roles: ['ROLE_SALES'],
    })

    useAuthStore.getState().logout()

    const state = useAuthStore.getState()
    expect(state.isAuthenticated()).toBe(false)
    expect(state.token).toBeNull()
    expect(state.user).toBeNull()
  })

  it('保持しているロールを判定できる', () => {
    useAuthStore.getState().login({
      token: 'jwt-token',
      userId: 'tracker01',
      displayName: '佐藤花子',
      roles: ['ROLE_TRACKER'],
    })

    expect(useAuthStore.getState().hasAnyRole(['ROLE_TRACKER'])).toBe(true)
    expect(useAuthStore.getState().hasAnyRole(['ROLE_SALES'])).toBe(false)
    // 許可ロールを指定しない画面は、認証済みなら誰でも開ける（ダッシュボード等）
    expect(useAuthStore.getState().hasAnyRole([])).toBe(true)
  })

  it('未認証のときはどのロールも持たない', () => {
    expect(useAuthStore.getState().hasAnyRole(['ROLE_SALES'])).toBe(false)
    // 認証していなければ「ロール不問の画面」も開けない
    expect(useAuthStore.getState().hasAnyRole([])).toBe(false)
  })
})
