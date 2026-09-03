import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * 動作確認用ログインは opt-in である（ADR-0004 決定 1）。
 *
 * <p>SPA の設定はビルド時に成果物へ焼き込まれ、実行時には取り消せない。
 * 「イメージの既定が有効」だと、渡し忘れた環境がそのまま認証情報つきの画面を配る。
 * 既定値そのものを検査する。</p>
 */
describe('動作確認用ログインの既定値', () => {
  it('Dockerfile の既定は無効', () => {
    const dockerfile = readFileSync('Dockerfile', 'utf-8');

    // 読めていないと検査が空振りする。まず読めていることを確かめる。
    expect(dockerfile).toContain('ARG VITE_DEMO_LOGIN_ENABLED');
    expect(dockerfile).not.toContain('ARG VITE_DEMO_LOGIN_ENABLED=true');
  });

  it('本番相当のビルドが読む .env / .env.production では有効化しない', () => {
    for (const file of ['.env', '.env.production', '.env.local']) {
      let content: string;
      try {
        content = readFileSync(file, 'utf-8');
      } catch {
        continue; // 無いのが正しい状態。
      }
      expect(content, `${file} が動作確認用ログインを有効にしている`).not.toMatch(
        /VITE_DEMO_LOGIN_ENABLED\s*=\s*true/,
      );
    }
  });
});
