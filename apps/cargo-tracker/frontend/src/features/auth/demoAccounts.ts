import type { Role } from '@/shared/auth/roles';

/**
 * 動作確認用の利用者（ADR-0004）。
 *
 * <p>どの利用者 ID がどの担当かが画面から分からないと、ロール別の表示を確かめるたびに
 * シードの SQL を読みに行くことになる。authms の
 * {@code db/seed/R__demo_users.sql} と一致させること。食い違いは
 * {@code DemoAccountsMatchSeedTest} が赤にする。</p>
 */
export interface DemoAccount {
  readonly username: string;
  readonly description: string;
  readonly roles: readonly Role[];
  /** ログインできる利用者か。無効化されたアカウントの挙動を画面から確かめるために持つ。 */
  readonly canSignIn: boolean;
}

/**
 * 一覧の利用者すべてで共通のパスワード。画面にもそう書く。
 *
 * 開発環境の動作確認用であり、本番の利用者には存在しない（ADR-0004）。埋め込みは
 * 意図したもので、この値を知られても本番の利用者にはならない。抑止のコメントは
 * **指摘された行と同じ行**に置く（別の行に書いても効かない）。
 */
export const DEMO_PASSWORD = 'secret1234'; // NOSONAR: 開発環境の動作確認用（ADR-0004）

/**
 * 1 行 1 利用者で書けるようにする。
 *
 * <p>オブジェクトリテラルを 8 つ並べると、同じ形が繰り返されて名簿の差分が
 * 読み取りにくい。シードの SQL と突き合わせるときに見るのは利用者名・担当・
 * ログインの可否の 3 つだけなので、その 3 つが 1 行に並ぶ形にする。</p>
 *
 * @param username 利用者名
 * @param roles 担当（ロール）
 * @param description 画面に出す説明
 * @param canSignIn ログインできるか（無効化された利用者は false）
 * @returns 動作確認用の利用者
 */
function account(
  username: string,
  roles: readonly Role[],
  description: string,
  canSignIn = true,
): DemoAccount {
  return { username, description, roles, canSignIn };
}

export const DEMO_ACCOUNTS: readonly DemoAccount[] = [
  account('sales01', ['ROLE_SALES'], '営業担当者（荷主の登録・検索・要確認一覧）'),
  account('accountant01', ['ROLE_ACCOUNTANT'], '経理担当者（荷主一覧・要確認一覧）'),
  account('tracker01', ['ROLE_TRACKER'], '追跡管理者（要確認一覧）'),
  account('routing01', ['ROLE_ROUTING'], '経路設計者（IT1 時点では専用の画面はまだありません）'),
  account('handler01', ['ROLE_HANDLER'], '荷役担当者（IT1 時点では専用の画面はまだありません）'),
  account('shipper01', ['ROLE_SHIPPER'], '荷主（IT1 時点では専用の画面はまだありません）'),
  account('admin01', ['ROLE_ADMIN'], '管理者（IT1 時点では専用の画面はまだありません）'),
  account('disabled01', ['ROLE_SALES'], '無効化されたアカウント（ログインできないことの確認用）', false),
];
