import { describe, expect, it } from 'vitest';
import { ROLES } from '@/shared/auth/roles';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from './demoAccounts';
import { demoLoginOf } from './demoLogin';

describe('動作確認用ログインの設定', () => {
  it('既定は無効。書き忘れたら安全側に倒れる', () => {
    expect(demoLoginOf(undefined).enabled).toBe(false);
  });

  it('無効なときは利用者 ID もパスワードも一覧も持たない', () => {
    const demo = demoLoginOf(undefined);

    expect(demo.username).toBe('');
    expect(demo.password).toBe('');
    expect(demo.accounts).toEqual([]);
  });

  it('"true" 以外の値では有効にならない', () => {
    // "1" や "yes" を有効と読むと、書き方の揺れがそのまま漏れになる。
    for (const flag of ['1', 'yes', 'TRUE', 'false', '']) {
      expect(demoLoginOf(flag).enabled).toBe(false);
    }
  });

  it('有効なときだけ事前入力する', () => {
    const demo = demoLoginOf('true');

    expect(demo.enabled).toBe(true);
    expect(demo.username).toBe('sales01');
    expect(demo.password).toBe(DEMO_PASSWORD);
  });
});

describe('動作確認用の利用者', () => {
  it('全ロールを覆う。覆わないロールは画面を確かめる手段が無い', () => {
    const covered = new Set(DEMO_ACCOUNTS.flatMap((account) => account.roles));

    expect([...ROLES].filter((role) => !covered.has(role))).toEqual([]);
  });

  it('ログインできない利用者を含む', () => {
    // 一覧に載せる以上、実際にログインできないことまで確かめられなければ確認の役に立たない。
    expect(DEMO_ACCOUNTS.some((account) => !account.canSignIn)).toBe(true);
  });

  it('利用者 ID が重複しない', () => {
    const ids = DEMO_ACCOUNTS.map((account) => account.username);

    expect(new Set(ids).size).toBe(ids.length);
  });
});
