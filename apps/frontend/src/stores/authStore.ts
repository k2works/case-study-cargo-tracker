import { create } from 'zustand'

interface AuthState {
  token: string | null
  user: { username: string; roles: string[] } | null
  setToken: (token: string) => void
  setUser: (user: { username: string; roles: string[] }) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  setToken: (token) => set({ token }),
  setUser: (user) => set({ user }),
  logout: () => set({ token: null, user: null }),
}))
