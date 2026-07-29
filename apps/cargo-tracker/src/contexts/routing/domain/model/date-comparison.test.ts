import { describe, expect, it } from 'vitest';
import { isSameOrBeforeDate, isBeforeDate } from './date-comparison.js';

describe('Routing Context の日付単位比較', () => {
  it('同じ暦日なら時刻が期限より後でも期限内として扱う', () => {
    expect(
      isSameOrBeforeDate(
        new Date('2026-09-08T23:59:59Z'),
        new Date('2026-09-08T00:00:00Z'),
      ),
    ).toBe(true);
  });

  it('翌日到着は期限超過として扱う', () => {
    expect(
      isSameOrBeforeDate(
        new Date('2026-09-09T00:00:00Z'),
        new Date('2026-09-08T23:59:59Z'),
      ),
    ).toBe(false);
  });

  it('日付の前後だけを比較できる', () => {
    expect(isBeforeDate(new Date('2026-09-07T23:00:00Z'), new Date('2026-09-08T01:00:00Z'))).toBe(
      true,
    );
    expect(isBeforeDate(new Date('2026-09-08T00:00:00Z'), new Date('2026-09-08T23:00:00Z'))).toBe(
      false,
    );
  });
});
