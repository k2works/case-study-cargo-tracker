import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { EXCEPTION_TYPE_LABELS, REPORTABLE_EXCEPTION_TYPES } from './api';

/**
 * 例外種別の呼び名は要素表（domain-model.md）が正典。
 *
 * <p><b>書き写した呼び名は、正典が変わっても追随しない。</b> 読み取って突き合わせる
 * （`transportStatusLabels.test.ts` と同じ形）。</p>
 */
const CANON = '../../../docs/design/cargo-tracker/domain-model.md';

/** 要素表の「例外種別 `ExceptionType` | 遅延 / 破損 / ... | `DELAY` / ...」を読む。 */
function canonLabels(): Record<string, string> {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf('| 例外種別 `ExceptionType` |');
  expect(start, 'domain-model.md に例外種別の行が無い').toBeGreaterThan(-1);
  const cells = source.slice(start, source.indexOf('\n', start)).split('|');
  const labels = (cells[2] ?? '').trim().split(/\s*\/\s*/);
  const names = (cells[3] ?? '').trim().replaceAll('`', '').split(/\s*\/\s*/);
  expect(labels).toHaveLength(names.length);

  return Object.fromEntries(names.map((name, i) => [name, labels[i] ?? '']));
}

describe('例外種別の呼び名', () => {
  it('5 値すべてが正典と一致する', () => {
    const canon = canonLabels();

    expect(Object.keys(canon)).toHaveLength(5);
    expect(EXCEPTION_TYPE_LABELS).toEqual(canon);
  });

  it('手で起票できるのは、システムが自動で起票しない種別だけ', () => {
    // **誤配と税関保留は出さない。** どちらもシステムが起票する（US28・UC21）。
    // 手で選べるようにすると、起きていない誤配を記録できてしまう。
    expect(REPORTABLE_EXCEPTION_TYPES).toEqual(['DELAY', 'DAMAGE', 'LOSS']);
    expect(REPORTABLE_EXCEPTION_TYPES).not.toContain('MISROUTE');
    expect(REPORTABLE_EXCEPTION_TYPES).not.toContain('CUSTOMS_HOLD');
  });
});
