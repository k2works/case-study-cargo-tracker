import { Link } from 'react-router';

/** 403。何が足りないかは言うが、入力仕様は教えない。 */
export function ForbiddenPage() {
  return (
    <div role="alert">
      <h1>この画面を開く権限がありません</h1>
      <p>担当のロールが割り当てられていません。必要であれば管理者に依頼してください。</p>
      <Link to="/">ダッシュボードへ戻る</Link>
    </div>
  );
}
