import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { HANDLING_TYPE_LABELS } from './api';

/**
 * 荷役種別の呼び名は要素表（domain-model.md）が正典。
 *
 * <p><b>書き写した呼び名は、正典が変わっても追随しない。</b> 読み取って
 * 突き合わせる（`navigationMatchesUiDesign.test.ts` の先例）。</p>
 */
const CANON = '../../../docs/design/cargo-tracker/domain-model.md';

/** 要素表の「荷役種別 `HandlingType` | 受領 / 積込 / 荷降し / 引取 | `RECEIVE` / ...」を読む。 */
function canonLabels(): Record<string, string> {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf('| 荷役種別 `HandlingType` |');
  expect(start, 'domain-model.md に荷役種別の行が無い').toBeGreaterThan(-1);
  const cells = source.slice(start, source.indexOf('\n', start)).split('|');
  const labels = (cells[2] ?? '').trim().split(/\s*\/\s*/);
  const names = (cells[3] ?? '').trim().replaceAll('`', '').split(/\s*\/\s*/);
  expect(labels).toHaveLength(names.length);

  const canon: Record<string, string> = {};
  names.forEach((name, index) => {
    canon[name] = labels[index] as string;
  });
  return canon;
}

describe('荷役種別の呼び名', () => {
  it('4 値すべてが正典と一致する', () => {
    const canon = canonLabels();

    expect(Object.keys(canon)).toHaveLength(4);
    expect(HANDLING_TYPE_LABELS).toEqual(canon);
  });
});
