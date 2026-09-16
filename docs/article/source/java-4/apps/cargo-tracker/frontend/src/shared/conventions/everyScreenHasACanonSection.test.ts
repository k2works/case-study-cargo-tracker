import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * 画面一覧に行がある画面には、設計の節がある。
 *
 * <p><b>3 IT 続けて同じ欠落が出た。</b> IT12 の S52、IT13 の S60、IT14 の
 * S12・S13・S62——いずれも画面一覧の行・ナビ構成表・画面遷移図はあるのに、
 * <b>画面項目と操作手順を書いた節だけが無い</b>状態だった。行があるので
 * 「設計済み」に見え、実装する人は遷移図から想像することになる。</p>
 *
 * <p><b>人が気づく形では止まらなかったので機械に移す</b>（IT13 ふりかえり
 * Try T9「同じ欠落が 3 回出たら検査にする」）。</p>
 *
 * <p>節が無いこと自体は文書の欠落だが、実装に効く——節に書かれるのは
 * 「なぜその画面がそう振る舞うか」で、遷移図には載らない。</p>
 */
const CANON = '../../../docs/design/cargo-tracker/ui_design.md';

/**
 * 節を持たなくてよい画面。
 *
 * <p><b>名簿は短く保つ。</b> 「載っていないものを通す」形にすると、載せ忘れた
 * ものほど漏れる（ADR-0016 決定 3 と同じ考え方）。除外するなら理由を書く。</p>
 */
const WITHOUT_SECTION: Record<string, string> = {
  // ヘッダの [ログアウト] から呼ぶだけで、画面としての項目が無い。
  S03: 'ヘッダの操作であって画面ではない',
};

function canonSource(): string {
  return readFileSync(CANON, 'utf-8');
}

/** 画面一覧（`| ID | 画面 | ルート | ロール | UC | 反映中の扱い |`）の画面 ID。 */
function screenIdsInTable(): string[] {
  const source = canonSource();
  const start = source.indexOf('| ID | 画面 | ルート | ロール | UC | 反映中の扱い |');
  expect(start, 'ui_design.md に画面一覧の表が無い').toBeGreaterThan(-1);
  const table = source.slice(start, source.indexOf('\n\n', start));

  const ids: string[] = [];
  for (const line of table.split('\n').slice(2)) {
    const cells = line.split('|').map((cell) => cell.trim());
    const id = cells[1] ?? '';
    if (/^S\d{2}$/.test(id)) {
      ids.push(id);
    }
  }
  return ids;
}

/** `### S12: 見積作成` のような節見出しから画面 ID を読む。 */
function screenIdsWithSection(): string[] {
  return [...canonSource().matchAll(/^#{2,4} (S\d{2})[:：]/gm)].map((match) => match[1] as string);
}

describe('UI 設計の画面一覧と節', () => {
  it('画面一覧を読めている（検査が空振りしていない）', () => {
    // 名簿が読めていなければ「欠落 0 件」は常に真になる。
    expect(screenIdsInTable().length).toBeGreaterThanOrEqual(20);
    expect(screenIdsWithSection().length).toBeGreaterThanOrEqual(15);
  });

  it('画面一覧に行があって節が無い画面は無い', () => {
    const withSection = new Set(screenIdsWithSection());
    const missing = screenIdsInTable()
      .filter((id) => WITHOUT_SECTION[id] === undefined)
      .filter((id) => !withSection.has(id));

    expect(
      missing,
      '画面一覧に行はあるのに、画面項目と操作手順の節が無い（実装する人は遷移図から想像することになる）',
    ).toEqual([]);
  });

  it('節があるのに画面一覧に行が無い画面は無い（逆向きも見る）', () => {
    // 一覧を起点に走査するだけだと、**節だけ書いて一覧に足し忘れた画面**が
    // 素通りする。載せ忘れたものほど漏れるので、両方向から見る。
    const inTable = new Set(screenIdsInTable());
    const orphan = screenIdsWithSection().filter((id) => !inTable.has(id));

    expect(orphan, '節はあるのに画面一覧に無い（ロールとルートが決まっていない）')
      .toEqual([]);
  });
});
