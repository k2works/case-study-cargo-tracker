import { describe, expect, it } from 'vitest';
import { ALERT, LINK, NOTICE } from './styles';

/**
 * 色は ui_design.md の「色トークン」表に対応させる。表の組み合わせは
 * コントラスト比 AA を満たすものとして選ばれているので、明るい方へずらすと
 * 基準を割る（例: blue-500 は白地に 3.7 : 1）。
 *
 * <p>設計に書いてあるだけでは守られない。値が動いたらここで赤になる。</p>
 */
describe('色トークン（ui_design.md）', () => {
  it('リンクは text-link（#1D4ED8 = blue-700）', () => {
    expect(LINK).toContain('text-blue-700');
  });

  it('案内は badge-pending（#B45309 on #FFFBEB = amber-700 on amber-50）', () => {
    expect(NOTICE).toContain('text-amber-700');
    expect(NOTICE).toContain('bg-amber-50');
  });

  it('失敗は badge-danger（#991B1B on #FEE2E2 = red-800 on red-100）', () => {
    expect(ALERT).toContain('text-red-800');
    expect(ALERT).toContain('bg-red-100');
  });
});
