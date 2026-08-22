import { DEMO_ACCOUNTS } from '../config/demo-login'
import type { Role } from '../types/role'

export type MockUser = {
  password: string
  displayName: string
  roles: Role[]
  /** 無効化されたアカウントは、パスワードが正しくてもログインできない（US31）。 */
  enabled: boolean
}

/** 一覧の利用者すべてで共通のパスワード。 */
export const MOCK_PASSWORD = 'password'

/** 画面に出す呼び名。実際のシード（V3/V4）と揃える。 */
const DISPLAY_NAMES: Record<string, string> = {
  sales01: '山田太郎',
  routing01: '田中次郎',
  tracker01: '佐藤花子',
  handler01: '鈴木一郎',
  accountant01: '高橋美咲',
  shipper01: '伊藤商事',
  admin01: '管理 太郎',
  disabled01: '退職済 太郎',
}

/**
 * 開発・テスト用の利用者。
 *
 * <p>ログイン画面の一覧（DEMO_ACCOUNTS）から導出する。別々に持つと、画面に並べた利用者を
 * 選んでも「ID かパスワードが正しくありません」になる状態が生まれる。一覧は
 * 「確かめられる利用者」の名簿であって、確かめられないものを載せてはいけない。
 */
export const MOCK_USERS: Record<string, MockUser> = Object.fromEntries(
  DEMO_ACCOUNTS.map((account) => [
    account.userId,
    {
      password: MOCK_PASSWORD,
      displayName: DISPLAY_NAMES[account.userId] ?? account.userId,
      roles: account.roles,
      enabled: account.canLogIn,
    },
  ]),
)
