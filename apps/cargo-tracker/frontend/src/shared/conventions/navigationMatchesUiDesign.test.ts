import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { NAVIGATION } from '@/shared/ui/navigation';

/**
 * サイドナビの内容は UI 設計の正典と一致する。
 *
 * <p>画面の到達性の正典は navigation.ts で、その内容の正典は ui_design.md の
 * 「サイドナビに載る画面」の表。<b>書き写すと、正典が変わったときに追随しない。</b>
 * 読み取って突き合わせる。</p>
 *
 * <p>画面 ID（S02 など）で突き合わせる。ラベルは文言の調整で動くので、動いただけで
 * 赤くなると、検査が邪魔になって消される。</p>
 */
const CANON = '../../../docs/design/cargo-tracker/ui_design.md';

/** ui_design.md の「サイドナビ項目 | 画面 | 表示ロール」の表から画面 ID を読む。 */
function canonScreenIds(): string[] {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf('| サイドナビ項目 | 画面 | 表示ロール |');
  expect(start, 'ui_design.md にサイドナビの表が無い').toBeGreaterThan(-1);
  const table = source.slice(start, source.indexOf('\n\n', start));
  const ids: string[] = [];
  for (const line of table.split('\n').slice(2)) {
    const cells = line.split('|').map((cell) => cell.trim());
    if (cells.length > 2 && /^S\d{2}$/.test(cells[2] ?? '')) {
      ids.push(cells[2] as string);
    }
  }
  return ids;
}

/** navigation.ts の項目に対応する画面 ID（ui_design.md の画面一覧のルートで対応）。 */
const SCREEN_OF_PATH: Record<string, string> = {
  '/': 'S02',
  '/shippers': 'S10',
  '/shippers/new': 'S11',
  '/bookings': 'S20',
  '/bookings/new': 'S21',
  '/routing/worklist': 'S30',
  '/voyages': 'S32',
  '/voyages/new': 'S33',
  '/worklist/attention': 'S70',
  '/admin/users': 'S90',
};

describe('サイドナビと UI 設計', () => {
  it('正典の表を読めている（検査が空振りしていない）', () => {
    expect(canonScreenIds().length).toBeGreaterThanOrEqual(10);
  });

  it('ナビの項目はすべて正典の表に載っている', () => {
    const canon = canonScreenIds();
    const missing = NAVIGATION.map((item) => SCREEN_OF_PATH[item.path] ?? item.path).filter(
      (screen) => !canon.includes(screen),
    );

    expect(missing, 'ナビに出るのに正典に無い画面がある').toEqual([]);
  });

  it('ナビの項目には対応する画面 ID がある', () => {
    // 対応表に足し忘れると、上の検査が黙って素通りする。
    const unmapped = NAVIGATION.filter((item) => SCREEN_OF_PATH[item.path] === undefined);

    expect(unmapped.map((item) => item.path)).toEqual([]);
  });
});
