import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../stores/auth-store'
import type { Role } from '../types/role'

type Props = {
  /** 許可するロール。省略時は「認証済みなら誰でも」を意味する（ダッシュボード等）。 */
  allowedRoles?: Role[]
  children: ReactNode
}

/**
 * 画面の到達性を決めるガード。
 *
 * 未認証はログインへ、権限不足は 403 へ送る。両者を区別するのは、利用者にとって
 * 「ログインし直せば見られる」のか「そもそも自分の担当ではない」のかが別の話だから。
 */
export function RequireAuth({ allowedRoles = [], children }: Props) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())
  const hasAnyRole = useAuthStore((state) => state.hasAnyRole)
  const location = useLocation()

  if (!isAuthenticated) {
    // ログイン後に元の画面へ戻せるよう、行き先を覚えておく
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (!hasAnyRole(allowedRoles)) {
    return <Navigate to="/403" replace />
  }

  return <>{children}</>
}
