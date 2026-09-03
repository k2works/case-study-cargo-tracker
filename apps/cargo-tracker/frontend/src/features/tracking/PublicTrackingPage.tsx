import { Link, useParams } from 'react-router';
import { LINK, NOTICE, PAGE_TITLE } from '@/shared/ui/styles';

/**
 * 公開追跡照会（`/track/:trackingNumber`）。**認証不要**。
 *
 * <p>荷受人が追跡番号だけで問い合わせる入口。ロール別の到達性は認証済みの利用者に
 * しか働かないので、認証の外にも入口が要る（ui_design.md）。</p>
 *
 * <p>照会そのものは US18 で実装する。ここで place holder を置くのは、繋がらない
 * リンクを見せないため。押しても同じ画面に戻ると、利用者は壊れていると受け取る。</p>
 *
 * <p>見た目はログイン画面と揃える。認証の外にある 2 画面は、社外の荷受人が最初に
 * 見る画面である。ここだけ未装飾だと、同じシステムの画面だと受け取ってもらえない。</p>
 */
export function PublicTrackingPage() {
  const { trackingNumber } = useParams();

  return (
    <div className="min-h-screen bg-gray-50">
      <main className="mx-auto max-w-md p-8">
        <h1 className={PAGE_TITLE}>荷物の追跡</h1>
        <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>

        {trackingNumber !== undefined && (
          <p className="mt-6 text-sm text-gray-700">
            追跡番号: <code className="font-mono">{trackingNumber}</code>
          </p>
        )}

        {/* 「まだ使えない」ことは目立たせる。本文に紛れさせると、番号を打ち込んで
            反応が無いのを不具合と受け取られる。 */}
        <output className={`${NOTICE} mt-4`}>
          追跡番号による照会は、次のイテレーションで使えるようになります。
          お急ぎの場合は担当の営業までお問い合わせください。
        </output>

        <p className="mt-6 text-sm text-gray-600">
          <Link to="/portal" className={LINK}>
            別の追跡番号で照会する
          </Link>
        </p>
        <p className="mt-2 text-sm text-gray-600">
          <Link to="/login" className={LINK}>
            ログイン画面へ戻る
          </Link>
        </p>
      </main>
    </div>
  );
}
