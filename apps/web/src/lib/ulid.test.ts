import { describe, expect, it } from 'vitest';
import { generateUlid, isUlid } from './ulid';

const seeded = (values: number[]) => {
  let i = 0;
  return () => values[i++ % values.length]! / 256;
};

describe('ULID（集約 ID。ADR-003：クライアントが生成する）', () => {
  it('26 文字の Crockford Base32 で、先頭は 7 以下', () => {
    const id = generateUlid(() => new Date('2026-11-04T00:00:00Z'), seeded([1, 2, 3]));
    expect(id).toHaveLength(26);
    expect(id).toMatch(/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/);
    expect(isUlid(id)).toBe(true);
  });

  it('時刻が後のものは、文字列として大きい（時系列に並ぶ）', () => {
    const rnd = seeded([5, 9, 200]);
    const a = generateUlid(() => new Date('2026-11-04T00:00:00Z'), rnd);
    const b = generateUlid(() => new Date('2026-11-04T00:00:01Z'), rnd);
    expect(a < b).toBe(true);
  });

  it('同じ時刻・同じ乱数からは、同じ ULID（テストで再現できる）', () => {
    const clock = () => new Date('2026-11-04T00:00:00Z');
    expect(generateUlid(clock, seeded([7, 8, 9]))).toBe(generateUlid(clock, seeded([7, 8, 9])));
  });

  it('サーバーの ULID と同じ時刻の符号化：サーバーが生成した値を検証できる', () => {
    // Scala 側の UlidSpec の固定値（01ARZ3NDEKTSV4RRFFQ69G5FAV）
    expect(isUlid('01ARZ3NDEKTSV4RRFFQ69G5FAV')).toBe(true);
  });

  it('形式を満たさない文字列を拒む', () => {
    for (const bad of ['', 'x', '81ARZ3NDEKTSV4RRFFQ69G5FAV', '01ARZ3NDEKTSV4RRFFQ69G5FAI', '01ARZ3NDEKTSV4RRFFQ69G5FA']) {
      expect(isUlid(bad), bad).toBe(false);
    }
  });
});
