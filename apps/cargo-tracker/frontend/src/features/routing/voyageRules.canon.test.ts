import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { canUpdateSchedule } from './voyageRules';

/**
 * 画面の述語は、集約の不変条件 5 と同じ条件である。
 *
 * <p>ブラウザから集約は呼べないので写しを持つこと自体は避けられない。避けられる
 * のは、写しが黙ってずれること。<b>集約が見る条件が増えたらここが赤くなる。</b></p>
 */
const CANON =
  '../backend/routingms/src/main/java/com/example/cargotracker/routing'
  + '/domain/model/aggregates/Voyage.java';

/** updateSchedule の本体（コマンドハンドラの中身）を切り出す。 */
function updateScheduleBody(): string {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf('public void updateSchedule(');
  expect(start, '集約の updateSchedule を読めていない').toBeGreaterThan(-1);
  const end = source.indexOf('\n    }\n', start);
  return source.slice(start, end);
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
