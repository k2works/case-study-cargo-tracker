import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { Role } from '../types/role'

export type AuthenticatedUser = {
  userId: string
  displayName: string
  roles: Role[]
}

export type LoginResult = AuthenticatedUser & { token: string }

type AuthState = {
  token: string | null
  user: AuthenticatedUser | null
  login: (result: LoginResult) => void
  logout: () => void
  isAuthenticated: () => boolean
  hasAnyRole: (allowed: Role[]) => boolean
}

/**
 * 認証状態。
 *
 * トークンは sessionStorage に保持する（ADR-005）。メモリだけだと画面を再読み込みするたびに
 * ログインを求められて業務が止まり、localStorage だとタブを閉じた後も端末に残って
 * 共用端末で「ログアウトした」という利用者の理解が裏切られる。
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,

      login: ({ token, ...user }) => set({ token, user }),

      logout: () => set({ token: null, user: null }),

      isAuthenticated: () => get().token !== null,

      hasAnyRole: (allowed) => {
        const { user } = get()
        if (user === null) {
          return false
        }
        // 許可ロールを指定しない画面は、認証済みなら誰でも開ける（ダッシュボード等）。
        // ただし未認証は上で弾く。ここを逆にすると認証の外に業務画面が漏れる。
        if (allowed.length === 0) {
          return true
        }
        return allowed.some((role) => user.roles.includes(role))
      },
    }),
    {
      name: 'cargo-tracker-auth',
      storage: createJSONStorage(() => sessionStorage),
      // 関数は保存しない。状態だけを持ち越す
      partialize: (state) => ({ token: state.token, user: state.user }),
    },
  ),
)
