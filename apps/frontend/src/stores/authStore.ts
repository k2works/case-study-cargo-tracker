import { create } from 'zustand'
import type { UserInfo } from '../features/auth/types/auth'

const TOKEN_KEY = 'cargo_tracker_token'
const USER_KEY = 'cargo_tracker_user'

interface AuthState {
  token: string | null
  user: UserInfo | null
  setAuth: (token: string, user: UserInfo) => void
  logout: () => void
  isAuthenticated: () => boolean
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: sessionStorage.getItem(TOKEN_KEY),
  user: (() => {
    const saved = sessionStorage.getItem(USER_KEY)
    return saved ? JSON.parse(saved) as UserInfo : null
  })(),
  setAuth: (token, user) => {
    sessionStorage.setItem(TOKEN_KEY, token)
    sessionStorage.setItem(USER_KEY, JSON.stringify(user))
    set({ token, user })
  },
  logout: () => {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    set({ token: null, user: null })
  },
  isAuthenticated: () => get().token !== null,
}))
