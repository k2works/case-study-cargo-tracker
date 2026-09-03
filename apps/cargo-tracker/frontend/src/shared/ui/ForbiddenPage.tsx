import { Link } from 'react-router';
import { ALERT, LINK, PAGE_TITLE } from './styles';

/**
 * 403。何が足りないかは言うが、入力仕様は教えない。
 *
 * <p><strong>この画面はサイドナビの外に出る</strong>（RequireRole が `/403` へ
 * 送るため）。囲みの中にいる前提で書くと、背景も余白も無い画面になる。
 * ログイン画面と同じく、自分で画面全体を組む。</p>
 */
export function ForbiddenPage() {
  return (
    <div className="min-h-screen bg-gray-50">
      <main className="mx-auto max-w-md p-8">
        <h1 className={PAGE_TITLE}>この画面を開く権限がありません</h1>
        {/* role="alert" は見出しではなく本文に置く。見出しごと読み上げ領域に
            すると、遷移のたびに全文が読み上げられて何が起きたか分かりにくい。 */}
        <p role="alert" className={`${ALERT} mt-4`}>
          担当のロールが割り当てられていません。必要であれば管理者に依頼してください。
        </p>
        <p className="mt-6 text-sm">
          <Link to="/" className={LINK}>
            ダッシュボードへ戻る
          </Link>
        </p>
      </main>
    </div>
  );
}
