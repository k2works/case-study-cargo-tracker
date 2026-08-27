import type { Role } from '../types/role'

/**
 * 動作確認用の利用者。
 *
 * <p>どの ID がどの担当かが画面から分からないと、ロール別の表示を確かめるたびに
 * シードの SQL を読みに行くことになる。V3__seed_users.sql と対応させる。
 */
export type DemoAccount = {
  userId: string
  description: string
  roles: Role[]
  /** ログインできる利用者か。無効化アカウントの挙動（US31）を画面から確かめるために持つ。 */
  canLogIn: boolean
}

export const DEMO_ACCOUNTS: DemoAccount[] = [
  {
    userId: 'sales01',
    description: '営業担当者（荷主の登録・検索）',
    roles: ['ROLE_SALES'],
    canLogIn: true,
  },
  {
    userId: 'routing01',
    description: '経路設計者（航海スケジュール・経路設計）',
    roles: ['ROLE_ROUTING'],
    canLogIn: true,
  },
  {
    userId: 'tracker01',
    description: '追跡管理者（貨物状態・例外・キャンセル承認）',
    roles: ['ROLE_TRACKER'],
    canLogIn: true,
  },
  {
    userId: 'handler01',
    description: '荷役作業員（荷役作業・通関の登録）',
    roles: ['ROLE_HANDLER'],
    canLogIn: true,
  },
  {
    userId: 'accountant01',
    description: '経理担当者（請求・入金）',
    roles: ['ROLE_ACCOUNTANT'],
    canLogIn: true,
  },
  {
    userId: 'shipper01',
    description: '荷主（自分の貨物の追跡）',
    roles: ['ROLE_SHIPPER'],
    canLogIn: true,
  },
  {
    userId: 'shipper02',
    description: '荷主（紐付け未設定の確認）',
    roles: ['ROLE_SHIPPER'],
    canLogIn: true,
  },
  {
    userId: 'shipper03',
    description: '荷主（例外ありの貨物確認）',
    roles: ['ROLE_SHIPPER'],
    canLogIn: true,
  },
  {
    userId: 'admin01',
    description: 'システム管理者（ロックされたアカウントの解除）',
    roles: ['ROLE_ADMIN'],
    canLogIn: true,
  },
  {
    userId: 'disabled01',
    description: '無効化されたアカウント（ログインできないことの確認用）',
    roles: ['ROLE_SALES'],
    canLogIn: false,
  },
]

export type DemoLogin = {
  enabled: boolean
  userId: string
  password: string
  /** 一覧の利用者すべてで共通のパスワード。 */
  accounts: DemoAccount[]
}

/** 一覧の利用者すべてで共通のパスワード。 */
const SHARED_PASSWORD = 'password'

/** 事前入力する既定の利用者。最初に触るのは営業担当者の画面である。 */
const DEFAULT_USER_ID = 'sales01'

/**
 * 動作確認用ログインの設定を組み立てる。
 *
 * <p>**既定は無効である。** 有効化を明示した環境（ローカル・開発環境）でのみ効く。
 * 「本番でうっかり有効になる」経路を作らないため、安全側を既定にして opt-in にしている。
 * 有効時は画面に開発環境である旨を必ず表示する。事前入力されていることを利用者に隠すと、
 * 本番同様の画面だと思い込まれる。
 */
export function demoLoginOf(flag: string | undefined): DemoLogin {
  const enabled = flag === 'true'
  return {
    enabled,
    userId: enabled ? DEFAULT_USER_ID : '',
    password: enabled ? SHARED_PASSWORD : '',
    accounts: enabled ? DEMO_ACCOUNTS : [],
  }
}

export const DEMO_LOGIN = demoLoginOf(import.meta.env.VITE_DEMO_LOGIN_ENABLED)
