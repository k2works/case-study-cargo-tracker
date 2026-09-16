import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { STATUS_LABELS } from './TrackingDetailPage';

/**
 * 状態の呼び名は要素表（domain-model.md）が正典。
 *
 * <p><b>書き写した呼び名は、正典が変わっても追随しない。</b> 読み取って突き合わせる。
 * 画面が呼び名を持つのはサーバの応答と重複するが、選択肢のためだけに 1 往復
 * 増やさない代わりに、ずれたらここで赤にする。</p>
 */
const CANON = '../../../docs/design/cargo-tracker/domain-model.md';

/** 要素表の「輸送ステータス `TransportStatus`」の行から、日本語と列挙名を読む。 */
function canonLabels(): Record<string, string> {
  const source = readFileSync(CANON, 'utf-8');
  const start = source.indexOf('| 輸送ステータス `TransportStatus`');
  expect(start, 'domain-model.md に輸送ステータスの要素表が無い').toBeGreaterThan(-1);
  const table = source.slice(start, source.indexOf('| 荷役種別', start));
  const labels: Record<string, string> = {};
  for (const line of table.split('\n')) {
    const cells = line.split('|').map((cell) => cell.trim());
    // 2 列目が日本語、3 列目が `ENUM_NAME`。
    const name = /^`([A-Z_]+)`$/.exec(cells[3] ?? '')?.[1];
    if (name !== undefined && cells[2]) {
      labels[name] = cells[2] as string;
    }
  }
  return labels;
}

describe('輸送ステータスの呼び名', () => {
  it('9 値すべてが正典と一致する', () => {
    const canon = canonLabels();

    expect(Object.keys(canon)).toHaveLength(9);
    expect(STATUS_LABELS).toEqual(canon);
  });
});
