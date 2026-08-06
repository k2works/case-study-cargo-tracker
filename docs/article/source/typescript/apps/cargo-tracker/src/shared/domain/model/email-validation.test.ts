import { describe, expect, it } from 'vitest';
import { isValidEmail } from './email-validation.js';

describe('isValidEmail（ReDoS 非脆弱）', () => {
  it.each(['user@example.com', 'a.b@sub.example.co.jp', 'x@y.io'])(
    '正しい形式 "%s" を許可',
    (v) => {
      expect(isValidEmail(v)).toBe(true);
    },
  );

  it.each(['', 'invalid', 'a@b', 'no-at-mark.com', 'a@@b.com', 'a b@example.com', '@example.com', 'a@.com'])(
    '不正な形式 "%s" を拒否',
    (v) => {
      expect(isValidEmail(v)).toBe(false);
    },
  );

  it('254 文字超は拒否', () => {
    expect(isValidEmail(`${'a'.repeat(250)}@e.com`)).toBe(false);
  });

  it('病的な入力でも即座に判定する（ReDoS 回避）', () => {
    const evil = `${'a'.repeat(50000)}!`; // @ を含まない長大入力
    const start = performance.now();
    expect(isValidEmail(evil)).toBe(false);
    expect(performance.now() - start).toBeLessThan(50);
  });
});
