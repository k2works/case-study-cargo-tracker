import { describe, expect, it } from 'vitest';
import {
  businessDate,
  businessLocalToInstant,
  formatBusinessDateTime,
  instantToBusinessLocal,
} from './businessDate';

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

describe('入力欄と絶対時刻の往復', () => {
  it('入力した壁時計は業務タイムゾーンの時刻として送られる', () => {
    // 航海の出発を「2026-09-10 09:00」と入れたら、日本時間の朝 9 時である。
    // UTC として送ると、実際の出発より 9 時間遅い航海が登録され、そのまま
    // 経路候補の所要日数になる（エラーは出ない）。
    expect(businessLocalToInstant('2026-09-10T09:00')).toBe('2026-09-10T00:00:00Z');
  });

  it('絶対時刻は入力欄へ同じ壁時計で戻る', () => {
    expect(instantToBusinessLocal('2026-09-10T00:00:00Z')).toBe('2026-09-10T09:00');
  });

  it('往復しても値が動かない（更新画面で時刻がずれない）', () => {
    const instant = '2026-09-24T18:30:00Z';

    expect(businessLocalToInstant(instantToBusinessLocal(instant))).toBe(instant);
  });

  it('日付をまたぐ時刻でも往復する', () => {
    // 日本時間の 0 時台は UTC では前日。ここを素朴に扱うと 1 日ずれる。
    expect(businessLocalToInstant('2026-09-10T00:30')).toBe('2026-09-09T15:30:00Z');
    expect(instantToBusinessLocal('2026-09-09T15:30:00Z')).toBe('2026-09-10T00:30');
  });

  it('空文字は空文字のまま（未入力を 1970 年にしない）', () => {
    expect(businessLocalToInstant('')).toBe('');
    expect(instantToBusinessLocal('')).toBe('');
  });
});
