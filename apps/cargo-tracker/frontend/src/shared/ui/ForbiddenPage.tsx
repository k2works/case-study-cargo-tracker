import { Link } from 'react-router';
import { ALERT, LINK, PAGE_TITLE } from './styles';

/**
 * 403。何が足りないかは言うが、入力仕様は教えない。
 *
 * <p><strong>シェル（サイドナビ）の内側に出す。</strong> 権限の無い画面を開いた
 * だけで、その利用者が本来行ける画面への導線まで消えると、戻る手段が本文の
 * リンク 1 本になる（IT1 レビュー M2）。囲みは AppLayout が持つので、ここでは
 * 中身だけを書く。</p>
 */
export function ForbiddenPage() {
  return (
    <section>
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
    </section>
  );
}
