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

/** 一覧の利用者すべてで共通のパスワード。画面にもそう書く。 */
export const DEMO_PASSWORD = 'secret1234';

export const DEMO_ACCOUNTS: readonly DemoAccount[] = [
  {
    username: 'sales01',
    description: '営業担当者（荷主の登録・検索・要確認一覧）',
    roles: ['ROLE_SALES'],
    canSignIn: true,
  },
  {
    username: 'accountant01',
    description: '経理担当者（荷主一覧・要確認一覧）',
    roles: ['ROLE_ACCOUNTANT'],
    canSignIn: true,
  },
  {
    username: 'tracker01',
    description: '追跡管理者（要確認一覧）',
    roles: ['ROLE_TRACKER'],
    canSignIn: true,
  },
  {
    username: 'routing01',
    description: '経路設計者（IT1 時点では専用の画面はまだありません）',
    roles: ['ROLE_ROUTING'],
    canSignIn: true,
  },
  {
    username: 'handler01',
    description: '荷役担当者（IT1 時点では専用の画面はまだありません）',
    roles: ['ROLE_HANDLER'],
    canSignIn: true,
  },
  {
    username: 'shipper01',
    description: '荷主（IT1 時点では専用の画面はまだありません）',
    roles: ['ROLE_SHIPPER'],
    canSignIn: true,
  },
  {
    username: 'admin01',
    description: '管理者（IT1 時点では専用の画面はまだありません）',
    roles: ['ROLE_ADMIN'],
    canSignIn: true,
  },
  {
    username: 'disabled01',
    description: '無効化されたアカウント（ログインできないことの確認用）',
    roles: ['ROLE_SALES'],
    canSignIn: false,
  },
];
