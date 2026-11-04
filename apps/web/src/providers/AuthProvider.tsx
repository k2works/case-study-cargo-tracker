import { createContext, useContext, type ReactNode } from 'react';

export type Role = 'Shipper' | 'Sales' | 'RouteDesigner' | 'TrackingManager' | 'HandlingWorker' | 'Accountant';
export const ROLES: readonly Role[] = ['Shipper', 'Sales', 'RouteDesigner', 'TrackingManager', 'HandlingWorker', 'Accountant'];

export type CurrentUser = { userId: string; role: Role };

const AuthContext = createContext<CurrentUser | null>(null);

/**
 * 認証の境界（FE 5.1）。認証方式の詳細はこの中に閉じ込め、画面と feature は `useCurrentUser()` だけを使う。
 * 現在は**開発用のスタブ**（利用者とロールを外から渡す）。実際の認証（方式は BE-09）は Bolt 2 で、ここを差し替える。
 */
export function AuthProvider({ user, children }: { user: CurrentUser | null; children: ReactNode }) {
  return <AuthContext.Provider value={user}>{children}</AuthContext.Provider>;
}

/** ログインしていなければ null。 */
export function useCurrentUser(): CurrentUser | null {
  return useContext(AuthContext);
}

/** 認証情報を要求に付けるヘッダー（スタブ）。バックエンドの開発用の認証が有効なときだけ通る。 */
export function authHeaders(user: CurrentUser | null): Record<string, string> {
  return user ? { 'X-Debug-User-Id': user.userId, 'X-Debug-Role': user.role } : {};
}
