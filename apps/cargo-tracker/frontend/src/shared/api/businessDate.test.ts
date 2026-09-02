import { describe, expect, it } from 'vitest';
import { businessDate, formatBusinessDateTime } from './businessDate';

describe('業務日付', () => {
  it('日本時間の 0 時台は当日になる（UTC では前日）', () => {
    // 2026-09-03 00:30 JST = 2026-09-02 15:30 UTC
    const at = new Date('2026-09-02T15:30:00Z');

    expect(businessDate(at)).toBe('2026-09-03');
    // toISOString() を使っていたら前日になる。それがこのヘルパを置く理由。
    expect(at.toISOString().slice(0, 10)).toBe('2026-09-02');
  });

  it('日本時間の 23 時台も当日のまま', () => {
    // 2026-09-03 23:30 JST = 2026-09-03 14:30 UTC
    expect(businessDate(new Date('2026-09-03T14:30:00Z'))).toBe('2026-09-03');
  });

  it('日時は業務タイムゾーンで読む', () => {
    expect(formatBusinessDateTime('2026-09-02T15:30:00Z')).toContain('2026');
    expect(formatBusinessDateTime('2026-09-02T15:30:00Z')).toContain('00:30');
  });

  it('読めない値はそのまま返す（Invalid Date を出さない）', () => {
    expect(formatBusinessDateTime('not-a-date')).toBe('not-a-date');
  });
});
