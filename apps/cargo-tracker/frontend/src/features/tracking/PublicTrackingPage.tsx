import { Link, useParams } from 'react-router';

/**
 * 公開追跡照会（`/track/:trackingNumber`）。**認証不要**。
 *
 * <p>荷受人が追跡番号だけで問い合わせる入口。ロール別の到達性は認証済みの利用者に
 * しか働かないので、認証の外にも入口が要る（ui_design.md）。</p>
 *
 * <p>照会そのものは US18 で実装する。ここで place holder を置くのは、繋がらない
 * リンクを見せないため。押しても同じ画面に戻ると、利用者は壊れていると受け取る。</p>
 */
export function PublicTrackingPage() {
  const { trackingNumber } = useParams();

  return (
    <main>
      <h1>荷物の追跡</h1>
      {trackingNumber !== undefined && <p>追跡番号: {trackingNumber}</p>}
      <p role="status">
        追跡番号による照会は、次のイテレーションで使えるようになります。
        お急ぎの場合は担当の営業までお問い合わせください。
      </p>
      <p>
        <Link to="/login">ログイン画面へ戻る</Link>
      </p>
    </main>
  );
}
