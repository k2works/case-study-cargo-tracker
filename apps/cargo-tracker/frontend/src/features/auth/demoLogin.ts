import { DEMO_ACCOUNTS, DEMO_PASSWORD, type DemoAccount } from './demoAccounts';

export interface DemoLogin {
  readonly enabled: boolean;
  /** 事前入力する利用者 ID。無効なら空。 */
  readonly username: string;
  readonly password: string;
  readonly accounts: readonly DemoAccount[];
}

/** 事前入力する既定の利用者。IT1 で最初に触るのは営業担当者の画面である。 */
const DEFAULT_USERNAME = 'sales01';

/**
 * 動作確認用ログインの設定を組み立てる（ADR-0004）。
 *
 * <p><b>既定は無効。</b> 有効化を明示した環境でだけ効く。SPA の設定は
 * ビルド時に成果物へ焼き込まれ、実行時には取り消せない。「本番のイメージに
 * うっかり入る」経路を作らないため、書き忘れたら無効に倒れるようにしてある。</p>
 */
export function demoLoginOf(flag: string | undefined): DemoLogin {
  const enabled = flag === 'true';
  return {
    enabled,
    username: enabled ? DEFAULT_USERNAME : '',
    password: enabled ? DEMO_PASSWORD : '',
    accounts: enabled ? DEMO_ACCOUNTS : [],
  };
}

export const DEMO_LOGIN = demoLoginOf(import.meta.env.VITE_DEMO_LOGIN_ENABLED);
