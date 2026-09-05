import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { BOOKING_TRANSITIONS, canUpdateSpecification } from './transitions';

/**
 * 画面が持つ遷移表は、バックエンドの正典と同じ内容である。
 *
 * <p>ブラウザから集約は呼べないので、写しを持つこと自体は避けられない。避けられる
 * のは、写しが黙ってずれること。<b>正典に値を足したら、ここが赤くなる。</b></p>
 *
 * <p>載っている状態だけを比べない。Java 側の表を丸ごと読み取ってから突き合わせる。
 * 載っているものだけを数える検査は、載せ忘れたものほど漏らす。</p>
 */
// 実行時のカレントはフロントのルート（vitest の既定）。
const CANON =
  '../backend/bookingms/src/main/java/com/example/cargotracker/booking'
  + '/domain/model/valueobjects/BookingStatus.java';

function canonTransitions(): Record<string, string[]> {
  const source = readFileSync(CANON, 'utf-8');
  const map = source.slice(source.indexOf('NEXT = Map.of('), source.indexOf('public boolean'));
  const entries: Record<string, string[]> = {};
  // 「STATUS, EnumSet.of(A, B)」と「STATUS, EnumSet.noneOf(...)」の両方を拾う。
  for (const match of map.matchAll(/(\w+),\s*EnumSet\.(of|noneOf)\(([^)]*)\)/g)) {
    const status = match[1] ?? '';
    const kind = match[2];
    const body = match[3] ?? '';
    entries[status] =
      kind === 'noneOf'
        ? []
        : body.split(',').map((value) => value.trim()).filter((value) => value.length > 0);
  }
  return entries;
}

describe('予約の状態遷移表', () => {
  it('正典を読めている（検査が空振りしていない）', () => {
    expect(Object.keys(canonTransitions()).length).toBeGreaterThanOrEqual(9);
  });

  it('画面の写しは正典と丸ごと一致する', () => {
    expect(BOOKING_TRANSITIONS).toEqual(canonTransitions());
  });

  it('修正できる状態は正典と一致する（US32）', () => {
    // 遷移ではないので遷移表には出ない。Java 側の述語の本体を読み取って
    // 突き合わせる。正典が「仮受付だけ」でなくなったらここが赤くなる。
    const source = readFileSync(CANON, 'utf-8');
    const body = source.slice(source.indexOf('canUpdateSpecification()'));
    const canon = /return this == (\w+);/.exec(body)?.[1];

    expect(canon, '正典の述語を読めていない').toBeDefined();
    expect(canUpdateSpecification(canon as string)).toBe(true);
    for (const status of Object.keys(BOOKING_TRANSITIONS)) {
      expect(canUpdateSpecification(status)).toBe(status === canon);
    }
  });
});
