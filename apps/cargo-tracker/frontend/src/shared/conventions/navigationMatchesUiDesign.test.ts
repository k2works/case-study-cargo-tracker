import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { PAGES } from '@/app/routes';
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

/** ui_design.md の「サイドナビ項目 | 画面 | 表示ロール」の表から画面 ID と表示ロールを読む。 */
function canonRows(): { screen: string; roles: string[] }[] {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf('| サイドナビ項目 | 画面 | 表示ロール |');
  expect(start, 'ui_design.md にサイドナビの表が無い').toBeGreaterThan(-1);
  const table = source.slice(start, source.indexOf('\n\n', start));
  const rows: { screen: string; roles: string[] }[] = [];
  for (const line of table.split('\n').slice(2)) {
    const cells = line.split('|').map((cell) => cell.trim());
    if (cells.length > 3 && /^S\d{2}$/.test(cells[2] ?? '')) {
      rows.push({
        screen: cells[2] as string,
        roles: (cells[3] ?? '').split('、').map((role) => role.trim()).filter(Boolean),
      });
    }
  }
  return rows;
}

function canonScreenIds(): string[] {
  return canonRows().map((row) => row.screen);
}

/**
 * 正典が使う呼び名とロール定数の対応。**正典の側の言葉を写さない**ために、
 * 呼び名は `ROLE_LABELS` から導けるものを使い、ここは表記の差だけを吸収する。
 */
const ROLE_OF_CANON_LABEL: Record<string, string> = {
  営業: 'ROLE_SALES',
  経理: 'ROLE_ACCOUNTANT',
  追跡: 'ROLE_TRACKER',
  経路設計: 'ROLE_ROUTING',
  荷役: 'ROLE_HANDLER',
  荷主: 'ROLE_SHIPPER',
  管理者: 'ROLE_ADMIN',
};

const ALL_ROLES = Object.values(ROLE_OF_CANON_LABEL);

function canonRolesOf(labels: string[]): string[] {
  if (labels.includes('全ロール')) {
    return ALL_ROLES;
  }
  return labels.map((label) => {
    const role = ROLE_OF_CANON_LABEL[label];
    expect(role, `正典の表示ロール「${label}」に対応する定数が無い`).toBeDefined();
    return role as string;
  });
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

  it('実装した画面はすべてナビに載っている', () => {
    // 逆向き。ナビを起点に走査するだけだと、**正典と実装にあるのにナビへ
    // 足し忘れた画面**が緑のまま素通りする。載せ忘れたものほど漏れるので、
    // 名簿の側からも見る。
    const inNavigation = new Set(NAVIGATION.map((item) => item.path));
    const unreachable = Object.keys(PAGES).filter((path) => !inNavigation.has(path));

    expect(unreachable, '画面はあるのにサイドナビから辿り着けない').toEqual([]);
  });

  it('ナビの表示ロールが正典と一致する', () => {
    // ID だけを突き合わせても、**誤ったロールに開放した**ことは検出しない。
    // E2E は代表ロールしか踏まないので、網羅はここで見る。
    const canon = new Map(canonRows().map((row) => [row.screen, canonRolesOf(row.roles)]));
    const mismatched = NAVIGATION.flatMap((item) => {
      const screen = SCREEN_OF_PATH[item.path];
      const expected = screen === undefined ? undefined : canon.get(screen);
      if (expected === undefined) {
        return [];
      }
      const actual = [...item.allow].sort();
      return [...expected].sort().join(',') === actual.join(',')
        ? []
        : [{ screen, expected: [...expected].sort(), actual }];
    });

    expect(mismatched, 'サイドナビの表示ロールが正典と食い違う').toEqual([]);
  });
});
