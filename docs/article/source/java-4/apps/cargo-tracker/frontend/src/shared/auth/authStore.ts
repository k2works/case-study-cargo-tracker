import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { isRole, type Role } from './roles';

export interface AuthenticatedUser {
  readonly username: string;
  readonly roles: readonly Role[];
  readonly token: string;
}

interface AuthState {
  readonly user: AuthenticatedUser | null;
  login: (user: AuthenticatedUser) => void;
  logout: () => void;
  hasAnyRole: (roles: readonly Role[]) => boolean;
}

/**
 * 認証ストア。
 *
 * <p>置き場は sessionStorage。localStorage だとタブを閉じても残り、共用端末で
 * 次の人がそのまま入れる。</p>
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      login: (user) => set({ user }),
      logout: () => set({ user: null }),
      hasAnyRole: (roles) => {
        const user = get().user;
        if (!user) {
          return false;
        }
        return roles.some((role) => user.roles.includes(role));
      },
    }),
    {
      name: 'cargo-tracker-auth',
      storage: createJSONStorage(() => sessionStorage),
      // 保存されている値を信用しない。ロールが増減したあとの古い値で
      // 知らない画面に入れてしまうのを防ぐ。
      merge: (persisted, current) => {
        const saved = persisted as { user?: AuthenticatedUser } | undefined;
        const user = saved?.user;
        if (!user || !Array.isArray(user.roles) || !user.roles.every((r) => isRole(r))) {
          return current;
        }
        return { ...current, user };
      },
    },
  ),
);
