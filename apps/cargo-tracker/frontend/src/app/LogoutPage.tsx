import { useEffect } from 'react';
import { Navigate } from 'react-router';
import { useAuthStore } from '@/shared/auth/authStore';

/**
 * S03 ログアウト（US27）。
 *
 * <p><b>URL としてのログアウトが要る。</b> 正典は「ヘッダの `[ログアウト]` から
 * `/logout`（S03）へ」と書いているのに、ルートが無く `*` の受け皿（`/` への転送）に
 * 落ちていた——**認証されたままダッシュボードへ戻る**だけである（IT16 T1 の
 * ドリフト洗い出しで発見）。</p>
 *
 * <p><b>認証ストアと `sessionStorage` を破棄する。</b> 共用端末で次の人が
 * ブラウザバックしても戻れないようにする。</p>
 */
export function LogoutPage() {
  const logout = useAuthStore((state) => state.logout);

  // **描画のたびに走らせない。** 破棄は 1 度で足りる。
  useEffect(() => {
    logout();
    sessionStorage.clear();
  }, [logout]);

  // **待たずに送る。** 破棄は同期で終わるので、ここで残る理由が無い。
  return <Navigate to="/login" replace />;
}
