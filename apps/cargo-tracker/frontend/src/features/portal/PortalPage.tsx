import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { BUTTON_PRIMARY, FIELD, LABEL, LINK, PAGE_TITLE } from '@/shared/ui/styles';

/**
 * S01 ポータル（`/portal`）。**認証不要**。
 *
 * <p>荷受人はロールを持たない。ロール別の到達性は認証済みの利用者にしか働かないので、
 * 認証の外に入口を置く（ui_design.md 画面遷移図）。</p>
 *
 * <p>見た目はログイン画面・公開追跡と揃える。認証の外にある画面は社外の荷受人が
 * 最初に見る画面なので、ここだけ未装飾だと同じシステムだと受け取ってもらえない。</p>
 */
export function PortalPage() {
  const [trackingNumber, setTrackingNumber] = useState('');
  const navigate = useNavigate();

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = trackingNumber.trim();
    // 空のまま送ると追跡番号のない詳細画面に着く。押しても何も起きないほうがよい。
    if (value === '') {
      return;
    }
    navigate(`/track/${encodeURIComponent(value)}`);
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <main className="mx-auto max-w-md p-8">
        <h1 className={PAGE_TITLE}>荷物の追跡照会</h1>
        <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>

        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label htmlFor="trackingNumber" className={LABEL}>
              追跡番号
            </label>
            <input
              id="trackingNumber"
              name="trackingNumber"
              value={trackingNumber}
              onChange={(event) => setTrackingNumber(event.target.value)}
              className={FIELD}
            />
            <p className="mt-1 text-sm text-gray-600">
              お手元の書類に記載された追跡番号を入力してください
            </p>
          </div>

          <button type="submit" className={`${BUTTON_PRIMARY} w-full`}>
            照会する
          </button>
        </form>

        <p className="mt-6 text-sm text-gray-600">
          社内の担当者は
          <Link to="/login" className={LINK}>
            ログイン
          </Link>
          してください。
        </p>
      </main>
    </div>
  );
}
