import type { ReactNode } from 'react';

/** 状態を色だけで伝えない：種別を文言（先頭のラベル）でも示す。 */
export function Alert({ kind, children }: { kind: 'error' | 'warning' | 'info'; children: ReactNode }) {
  const label = { error: 'エラー', warning: '注意', info: 'お知らせ' }[kind];
  return (
    <div role={kind === 'info' ? 'status' : 'alert'} className={`alert alert-${kind}`}>
      <strong>{label}：</strong>
      {children}
    </div>
  );
}
