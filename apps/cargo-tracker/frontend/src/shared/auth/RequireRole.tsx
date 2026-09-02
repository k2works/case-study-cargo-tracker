import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuthStore } from './authStore';
import type { Role } from './roles';

interface Props {
  readonly allow: readonly Role[];
  readonly children: ReactNode;
}

/**
 * ロールで画面を守る。
 *
 * <p>未認証はログインへ、ロール違いは 403 へ送る。両方を 403 にすると、
 * ログインし直せば入れるのか、そもそも権限が無いのかが利用者に分からない。</p>
 */
export function RequireRole({ allow, children }: Props) {
  const user = useAuthStore((state) => state.user);
  const hasAnyRole = useAuthStore((state) => state.hasAnyRole);
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!hasAnyRole(allow)) {
    return <Navigate to="/403" replace />;
  }
  return <>{children}</>;
}
