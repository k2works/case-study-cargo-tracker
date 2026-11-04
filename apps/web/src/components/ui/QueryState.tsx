import type { ReactNode } from 'react';
import { Alert } from './Alert';
import { messageFor, type ApiError } from '@/lib/errors';

/** 読み込み中・失敗の共通表示。通信エラーと業務エラーを区別して案内する（FE 7.1）。 */
export function QueryState({ isPending, error, children }: { isPending: boolean; error: ApiError | null; children: ReactNode }) {
  if (error) return <Alert kind="error">{messageFor(error)}</Alert>;
  if (isPending) return <p>読み込み中…</p>;
  return <>{children}</>;
}
