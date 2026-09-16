import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { SCENARIOS } from '@/features/simulation/simulationApi';

/**
 * 選べるシナリオはサーバの列挙と一致する（US33 §受入基準 1 / US35 §受入基準 1）。
 *
 * <p><b>書き写すと追随しない。</b> 画面の一覧は呼び名の文字列で、サーバは
 * 知らない呼び名を断る——足し忘れると「選べないシナリオ」が、消し忘れると
 * 「選ぶと断られる項目」ができる。<b>読み取って突き合わせる</b>。</p>
 */
const CANON =
  '../backend/simulationms/src/main/java/com/example/cargotracker/simulation/'
  + 'domain/model/valueobjects/Scenario.java';

/** サーバの列挙が宣言している呼び名。 */
function serverScenarioLabels(): string[] {
  const source = readFileSync(CANON, 'utf-8');
  // 列挙の宣言は `NAME("呼び名", ...)` の形。**宣言の行だけを拾う**
  // ——本文中の文字列まで拾うと、検査が何を見ているか分からなくなる。
  const labels: string[] = [];
  for (const line of source.split('\n')) {
    const match = /^\s{4}([A-Z_]+)\("([^"]+)"/.exec(line);
    if (match) {
      labels.push(match[2] as string);
    }
  }
  return labels;
}

describe('選べるシナリオ', () => {
  it('正典を読めている（検査が空振りしていない）', () => {
    expect(serverScenarioLabels().length).toBeGreaterThanOrEqual(2);
  });

  it('画面の一覧とサーバの列挙が一致する', () => {
    // **順序も合わせる。** 画面の既定は先頭なので、並びが変わると
    // 「実行する」を押したときの既定が黙って変わる。
    expect([...SCENARIOS]).toEqual(serverScenarioLabels());
  });
});
