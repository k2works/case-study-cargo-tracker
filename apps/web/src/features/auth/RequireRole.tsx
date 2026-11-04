import type { ReactNode } from 'react';
import { canOpen } from './access';
import { useCurrentUser } from '@/providers/AuthProvider';

/** 許されたロールだけを通す。許されなければ 403 の画面（US26 AC-04）、ログインしていなければログインが必要な旨。 */
export function RequireRole({ path, children }: { path: string; children: ReactNode }) {
  const user = useCurrentUser();
  if (!user) {
    return (
      <main>
        <h1>ログインが必要です</h1>
        <p>画面を開くには、ログインしてください</p>
      </main>
    );
  }
  if (!canOpen(path, user.role)) {
    return (
      <main>
        <h1>権限がありません</h1>
        <p>この画面を開く権限がありません</p>
      </main>
    );
  }
  return <>{children}</>;
}
