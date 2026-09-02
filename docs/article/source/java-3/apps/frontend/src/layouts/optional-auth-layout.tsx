import { Outlet } from 'react-router-dom'
import { useAuthStore } from '../stores/auth-store'
import { AppLayout } from './app-layout'

/**
 * 認証の外にある画面を、**ログイン済みなら共通レイアウトの中に置く**。
 *
 * <p>公開の追跡照会（US18-5）は認証を求めない。しかし業務利用者はサイドバーの
 * 「貨物追跡」から来る——そこでメニューが消えると、**戻る手段がブラウザバック
 * しか無くなる**。自分がどのロールでどこにいるのかも分からなくなる。
 *
 * <p><strong>「認証の外に置く」と「ログイン済みの人にメニューを出さない」は別である。</strong>
 * 認証を求めないのは荷主のためであり、業務利用者の導線を切る理由にはならない。
 *
 * <p>未ログインのときは何も被せない。荷主に業務メニューを見せない
 * ——押せない項目が並ぶだけで、ここが自分の画面だと伝わらなくなる。
 */
export function OptionalAuthLayout() {
  const user = useAuthStore((state) => state.user)

  // 本文の領域（`main`）は<strong>どちらか一方だけが置く</strong>。
  // 共通レイアウトは中に `main` を持つため、被せるときは画面側に持たせない
  return user === null ? (
    <main>
      <Outlet />
    </main>
  ) : (
    <AppLayout />
  )
}
