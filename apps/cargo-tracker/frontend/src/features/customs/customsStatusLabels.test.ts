import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { CUSTOMS_SETTLED_STATUSES, SEARCHABLE_STATUSES, UPDATABLE_STATUSES } from './api';

/**
 * 通関状態の呼び名は要素表（domain-model.md）が正典。
 *
 * <p><b>書き写した呼び名は、正典が変わっても追随しない。</b> 読み取って突き合わせる
 * （`exceptionTypeLabels.test.ts` と同じ形）。</p>
 *
 * <p>`api.ts` は「呼び名はサーバが返す。画面で対応表を持たない」と書きながら、
 * 絞り込みと更新の選択肢では持っている——<b>持たざるを得ない</b>（選択肢はサーバが
 * 返さない）。持つなら正典と突き合わせる、が本プロジェクトの流儀である
 * （IT12 レビュー 高）。</p>
 */
const CANON = '../../../docs/design/cargo-tracker/domain-model.md';

/** 要素表の「通関状態 `CustomsStatus` | 審査中 / … | `PENDING` / …」を読む。 */
function canonLabels(): Record<string, string> {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf('| 通関状態 `CustomsStatus` |');
  expect(start, 'domain-model.md に通関状態の行が無い').toBeGreaterThan(-1);
  const cells = source.slice(start, source.indexOf('\n', start)).split('|');
  const labels = (cells[2] ?? '').trim().split(/\s*\/\s*/);
  const names = (cells[3] ?? '').trim().replaceAll('`', '').split(/\s*\/\s*/);
  expect(labels).toHaveLength(names.length);

  return Object.fromEntries(names.map((name, i) => [name, labels[i] ?? '']));
}

describe('通関状態の呼び名', () => {
  it('絞り込みの選択肢が 4 値すべて正典と一致する', () => {
    const canon = canonLabels();

    expect(Object.keys(canon)).toHaveLength(4);
    const searchable = Object.fromEntries(SEARCHABLE_STATUSES
      .filter((option) => option.value !== '')
      .map((option) => [option.value, option.label]));
    expect(searchable).toEqual(canon);
  });

  it('更新の選択肢は審査中を除いた 3 値で、呼び名は正典と一致する', () => {
    // **審査中には戻せない。** 集約が断るので選択肢にも出さない。
    const canon = canonLabels();

    expect(UPDATABLE_STATUSES.map((option) => option.value))
      .toEqual(['CLEARED', 'HELD', 'REJECTED']);
    for (const option of UPDATABLE_STATUSES) {
      expect(option.label).toBe(canon[option.value]);
    }
  });

  it('決着した状態は通関済と不可（引取を許すのは通関済だけ）', () => {
    // 正典の備考「引取を許すのは `CLEARED` だけ」に対応する。決着の判定を
    // 画面が持つのは更新の口を閉じるためで、サーバの `unsettled` の裏である。
    expect(CUSTOMS_SETTLED_STATUSES).toEqual(['CLEARED', 'REJECTED']);
    expect(CUSTOMS_SETTLED_STATUSES).not.toContain('PENDING');
    expect(CUSTOMS_SETTLED_STATUSES).not.toContain('HELD');
  });
});
