import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useAuthStore.setState({ token: null, user: null })
  })

  it('初期状態では未認証', () => {
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
  })

  it('setAuth でトークンとユーザー情報が保存される', () => {
    const token = 'test-token'
    const user = { username: 'admin', roles: ['ADMIN'] }

    useAuthStore.getState().setAuth(token, user)

    expect(useAuthStore.getState().token).toBe(token)
    expect(useAuthStore.getState().user).toEqual(user)
    expect(useAuthStore.getState().isAuthenticated()).toBe(true)
    expect(sessionStorage.getItem('cargo_tracker_token')).toBe(token)
  })

  it('logout でトークンとユーザー情報が削除される', () => {
    useAuthStore.getState().setAuth('token', { username: 'admin', roles: ['ADMIN'] })
    useAuthStore.getState().logout()

    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
    expect(sessionStorage.getItem('cargo_tracker_token')).toBeNull()
  })
})
