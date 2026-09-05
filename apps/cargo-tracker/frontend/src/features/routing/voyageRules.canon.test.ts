import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { canCancel, canUpdateSchedule } from './voyageRules';

/**
 * 画面の述語は、集約の不変条件 5 と同じ条件である。
 *
 * <p>ブラウザから集約は呼べないので写しを持つこと自体は避けられない。避けられる
 * のは、写しが黙ってずれること。<b>集約が見る条件が増えたらここが赤くなる。</b></p>
 */
const CANON =
  '../backend/routingms/src/main/java/com/example/cargotracker/routing'
  + '/domain/model/aggregates/Voyage.java';

/** コマンドハンドラの本体を切り出す。 */
function handlerBody(signature: string): string {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf(signature);
  expect(start, `集約の ${signature} を読めていない`).toBeGreaterThan(-1);
  const end = source.indexOf('\n    }\n', start);
  return source.slice(start, end);
}

function updateScheduleBody(): string {
  return handlerBody('public void updateSchedule(');
}

describe('航海を更新できる条件', () => {
  it('集約が断る条件は「登録されていない」と「キャンセル済み」だけ', () => {
    const body = updateScheduleBody();
    const guards = [...body.matchAll(/throw new IllegalTransition/g)];

    expect(guards, '集約の守りを読めていない').toHaveLength(2);
    expect(body).toContain('voyageNumber == null');
    expect(body).toContain('if (cancelled)');
  });

  it('画面の述語はキャンセル済みだけを断る', () => {
    // 集約に条件が増えたら上の検査が赤くなり、ここも直すことになる。
    expect(canUpdateSchedule({ cancelled: false })).toBe(true);
    expect(canUpdateSchedule({ cancelled: true })).toBe(false);
  });
});

describe('航海をキャンセルできる条件', () => {
  it('集約が断るのは「登録されていない」「既にキャンセル済み」「理由が無い」', () => {
    const body = handlerBody('public void cancel(');

    expect([...body.matchAll(/throw new IllegalTransition/g)]).toHaveLength(2);
    expect(body).toContain('voyageNumber == null');
    expect(body).toContain('if (cancelled)');
    // 理由の必須は業務規則なので集約が持つ。画面だけで守ると API を直接叩けば通る。
    expect(body).toContain('BusinessRuleViolation');
  });

  it('画面の述語はキャンセル済みだけを断る', () => {
    // 「登録されていない」は画面が持てない（開けないので）。
    expect(canCancel({ cancelled: false })).toBe(true);
    expect(canCancel({ cancelled: true })).toBe(false);
  });
});
