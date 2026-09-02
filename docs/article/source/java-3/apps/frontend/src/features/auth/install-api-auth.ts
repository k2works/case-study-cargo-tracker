import { configureAuth } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'

/**
 * API クライアントに認証状態を繋ぐ。
 *
 * <p>これを呼ばないと `configureAuth` の既定（トークンなし）のままになり、業務 API は
 * すべて 401 になる。ライブラリ層がストアを直接参照しないための間接であり、
 * 呼び忘れると静かに壊れるため、テストで「トークンが載ること」を固定する。
 */
export function installApiAuth() {
  configureAuth(
    () => useAuthStore.getState().token,
    // 期限切れのトークンを持ったままだと、画面は開くのに何も取得できない状態が続く
    () => useAuthStore.getState().logout(),
  )
}
