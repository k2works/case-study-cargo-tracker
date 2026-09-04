import { readFileSync } from 'node:fs';
import { globSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * 画面に出す文言に内部のストーリー ID（US07 など）を混ぜない。
 *
 * <p>利用者には意味が無く、「US07 とは何か」という問い合わせになる。実装の都合は
 * コメントに書く。<b>コメントと JSX の文言を区別して見る</b>ので、
 * 「US07 で入れる」という設計意図の注記は残せる。</p>
 */
// テストの名前は対象のストーリーを示すので除く。ここが見たいのは
// **利用者の目に入る文言**だけ。
const SOURCES = globSync('src/**/*.tsx').filter((file) => !file.endsWith('.test.tsx'));

/** 行コメント・ブロックコメントを落とす。残るのがコードと文言。 */
function withoutComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

describe('画面の文言', () => {
  it('検査対象の画面がある（空振りしていない）', () => {
    expect(SOURCES.length).toBeGreaterThan(10);
  });

  it('ストーリー ID を含まない', () => {
    const offenders = SOURCES.flatMap((file) => {
      const body = withoutComments(readFileSync(file, 'utf-8'));
      const hits = body.match(/US\d{2}/g) ?? [];
      return hits.length === 0 ? [] : [`${file}: ${[...new Set(hits)].join(', ')}`];
    });

    expect(offenders, '画面の文言に内部のストーリー ID が出ている').toEqual([]);
  });
});
