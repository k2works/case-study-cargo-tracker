import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { ROUTING_STATUS_LABELS } from './api';
import {
  BOOKING_TRANSITIONS,
  canAssignRoute,
  canNotifyShipper,
  canRequestConditionReview,
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
const ROUTING_CANON =
  '../backend/bookingms/src/main/java/com/example/cargotracker/booking'
  + '/domain/model/valueobjects/RoutingStatus.java';

const ROUTING_STATUSES = ['NOT_ROUTED', 'ROUTING_REQUESTED', 'ROUTED', 'MISROUTED'];

/**
 * 正典の述語の本体を読み、許している状態を全部返す（IT7 H.3）。
 *
 * <p><b>述語の本体だけを読む。</b> 集約の `!=` 比較を直接読む形だと、集約が
 * 述語に寄せ替えたときに検査が空振りする。判断は列挙が持ち、集約も画面も
 * それを呼ぶ、という形に揃える。</p>
 *
 * <p><b>拾えた数を返す側で数える。</b> 正典に条件が増えたのに 1 件目だけを
 * 比べると、画面の述語が古いままでも緑になる（IT6 レビュー 中）。</p>
 */
function canonPredicate(file: string, name: string): string[] {
  const source = readFileSync(file, 'utf-8');
  const start = source.indexOf(`${name}()`);
  expect(start, `${name} が正典に無い`).toBeGreaterThan(-1);
  const body = source.slice(start);
  const statement = /return ([^;]+);/.exec(body)?.[1];
  expect(statement, `${name} の本体を読めていない`).toBeTruthy();
  // `this == A` の並びを全部拾う。`||` でつないだ形も 1 つだけの形も同じに扱う。
  const allowed = [...(statement as string).matchAll(/this == (\w+)/g)]
    .map((match) => match[1] as string);
  expect(allowed.length, `${name} が this == の形でない`).toBeGreaterThan(0);
  return allowed;
}

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
    const canon = canonPredicate(CANON, 'canRequestRouting');
    for (const status of Object.keys(BOOKING_TRANSITIONS)) {
      expect(canRequestRouting(status), status).toBe(canon.includes(status));
    }
  });

  it('修正できる状態は正典と一致する（US32）', () => {
    // 遷移ではないので遷移表には出ない。Java 側の述語の本体を読み取って
    // 突き合わせる。正典が「仮受付だけ」でなくなったらここが赤くなる。
    const canon = canonPredicate(CANON, 'canUpdateSpecification');
    for (const status of Object.keys(BOOKING_TRANSITIONS)) {
      expect(canUpdateSpecification(status), status).toBe(canon.includes(status));
    }
  });

  it('通知できる状態は正典と一致する（US12）', () => {
    // 判断は RoutingStatus が持ち、集約も画面もそれを呼ぶ（IT7 H.3）。
    const canon = canonPredicate(ROUTING_CANON, 'canNotifyShipper');
    for (const status of ROUTING_STATUSES) {
      expect(canNotifyShipper(status), status).toBe(canon.includes(status));
    }
  });

  it('経路を確定できる状態は正典と一致する（US09）', () => {
    // **これまで突き合わせが無かった**（IT6 引き継ぎ 8c）。誤配からの再設計を
    // 許すか否かが、集約と画面で別々に書かれていた。
    const canon = canonPredicate(ROUTING_CANON, 'canAssignRoute');
    expect(canon, '正典が 2 つの状態を許さなくなった').toHaveLength(2);
    for (const status of ROUTING_STATUSES) {
      expect(canAssignRoute(status), status).toBe(canon.includes(status));
    }
  });

  it('経路設計へ戻せる状態は正典と一致する（US12）', () => {
    const canon = canonPredicate(CANON, 'canReturnToRouting');
    for (const status of Object.keys(BOOKING_TRANSITIONS)) {
      expect(canReturnToRouting(status), status).toBe(canon.includes(status));
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

  it('差し戻せる状態は正典と一致する（US10 §4 / ADR-0009 決定 2）', () => {
    // **経路を確定できるか（canAssignRoute）とは別の判断。** あちらは誤配からの
    // 再設計を許すが、差し戻しは許さない。同じ述語で出し分けると、誤配の予約で
    // ボタンが押せて 422 になる（IT6 レビュー 中）。
    const canon = canonPredicate(ROUTING_CANON, 'canRequestConditionReview');
    for (const status of ROUTING_STATUSES) {
      expect(canRequestConditionReview(status), status).toBe(canon.includes(status));
    }
  });
});
