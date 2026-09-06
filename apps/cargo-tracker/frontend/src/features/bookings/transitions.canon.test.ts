import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { ROUTING_STATUS_LABELS } from './api';
import {
  BOOKING_TRANSITIONS,
  canNotifyShipper,
  canRequestRouting,
  canReturnToRouting,
  canUpdateSpecification,
} from './transitions';

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
/** 状態そのものでなく「集約が何を許すか」を読むための正典。 */
const CARGO =
  '../backend/bookingms/src/main/java/com/example/cargotracker/booking'
  + '/domain/model/aggregates/Cargo.java';

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

  it('引き渡せる状態は正典と一致する（US06）', () => {
    // 遷移表には出ない。ROUTE_PROPOSED への自己遷移は経路の確定と条件の調整の
    // もので、引き渡しではないため。Java 側の述語の本体を読み取って突き合わせる。
    const source = readFileSync(CANON, 'utf-8');
    const body = source.slice(source.indexOf('canRequestRouting()'));
    const canon = /return this == (\w+);/.exec(body)?.[1];

    expect(canon, '正典の述語を読めていない').toBeDefined();
    for (const status of Object.keys(BOOKING_TRANSITIONS)) {
      expect(canRequestRouting(status), status).toBe(status === canon);
    }
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

  it('通知できる状態は正典と一致する（US12）', () => {
    // 集約は routingStatus で判断する。正典の本体を読み取って突き合わせる。
    const source = readFileSync(CARGO, 'utf-8');
    const body = source.slice(source.indexOf('public String notifyShipper'));
    const canon = /routingStatus != RoutingStatus\.(\w+)/.exec(body)?.[1];

    expect(canon, '正典の判断を読めていない').toBeDefined();
    for (const status of ['NOT_ROUTED', 'ROUTING_REQUESTED', 'ROUTED', 'MISROUTED']) {
      expect(canNotifyShipper(status), status).toBe(status === canon);
    }
  });

  it('経路設計へ戻せる状態は正典と一致する（US12）', () => {
    const source = readFileSync(CARGO, 'utf-8');
    const body = source.slice(source.indexOf('public String returnToRouting'));
    const canon = /bookingStatus != BookingStatus\.(\w+)/.exec(body)?.[1];

    expect(canon, '正典の判断を読めていない').toBeDefined();
    for (const status of Object.keys(BOOKING_TRANSITIONS)) {
      expect(canReturnToRouting(status), status).toBe(status === canon);
    }
  });

  it('経路設定状態の呼び名は設計と一致する（US10・US12）', () => {
    // **利用者に見せる文字列は、画面から踏むテストでしか固定されない。** IT6 で
    // `ROUTING_REQUESTED` の呼び名が実装だけ「設計依頼済み」になっていた（正典は
    // 「設計依頼中」）。要素表の呼び名を読んで突き合わせる。
    //
    // 画面の呼び名は正典で**始まる**ことを見る。`MISROUTED` は「誤配（再設計が
    // 要る）」のように、次の行動を添えて出しているため。添えた分を許しても、
    // 「設計依頼済み」と「設計依頼中」のような食い違いは捕まる。
    const canon = readFileSync(
      '../../../docs/design/cargo-tracker/domain-model.md', 'utf-8');
    for (const [status, label] of Object.entries(ROUTING_STATUS_LABELS)) {
      const row = canon.split('\n').find((line) =>
        line.startsWith('|') && line.includes(`\`${status}\``));
      expect(row, `${status} が要素表に無い`).toBeDefined();
      const canonLabel = (row as string).split('|')[2]?.trim();
      expect(canonLabel, `${status} の呼び名を読めていない`).toBeTruthy();
      expect(label, `${status} の呼び名が設計と食い違う`).toMatch(
        new RegExp(`^${canonLabel}`),
      );
    }
  });
});
