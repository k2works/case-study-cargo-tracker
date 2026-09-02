import { resolveNavigationItem } from '../../config/navigation'
import type { Role } from '../../types/role'

/** ログインの直後に必ず開ける画面。 */
export const DEFAULT_DESTINATION = '/dashboard'

/**
 * ログインしたあとの行き先を決める。
 *
 * <p>**覚えていた行き先は「前に開こうとした人」のもの**であり、いま入った人のもの
 * ではない。そのまま送ると、利用者を切り替えたときに担当外の画面へ着地し、
 * **入った直後に「この操作を行う権限がありません」と出る**（利用者からの申告）。
 *
 * <p>**開けるかどうかの判定は書き直さない。** ナビゲーションの定義（`NAVIGATION`）が
 * 画面とロールの対応を 1 か所で持っており、共通レイアウトも同じ規則で選択状態を
 * 決めている——ここに写しを作ると、片方だけが古くなる。
 *
 * @param from 覚えていた行き先。無ければ undefined
 * @param hasAnyRole そのロールを持っているか（`useAuthStore` の判定をそのまま渡す）
 */
export function destinationAfterLogin(
  from: string | undefined,
  hasAnyRole: (roles: Role[]) => boolean,
): string {
  if (from === undefined || from === '') {
    return DEFAULT_DESTINATION
  }
  const item = resolveNavigationItem(from)
  if (item === undefined) {
    // **知らない行き先へは送らない。**開けるかどうかを判断できないものへ送ると、
    // また入った直後に断られる
    return DEFAULT_DESTINATION
  }
  return hasAnyRole(item.roles) ? from : DEFAULT_DESTINATION
}
