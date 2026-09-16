import { describe, expect, it } from 'vitest';
import { isWellFormedTrackingNumber, normalizeTrackingNumber } from './api';

describe('追跡番号の入力（S44 / ui_design.md「入力形式」）', () => {
  it('大文字小文字とハイフンの有無を吸収する', () => {
    // 荷受人は案内のメールから手で打ち直す。
    expect(normalizeTrackingNumber('trk-8k2qx7m4rb')).toBe('TRK-8K2QX7M4RB');
    expect(normalizeTrackingNumber('  8K2QX7M4RB ')).toBe('TRK-8K2QX7M4RB');
    expect(normalizeTrackingNumber('TRK8K2QX7M4RB')).toBe('TRK-8K2QX7M4RB');
  });

  it('形式が違えば照会前に分かる', () => {
    expect(isWellFormedTrackingNumber('trk-8k2qx7m4rb')).toBe(true);
    // 10 桁でない・記号が混じる。
    expect(isWellFormedTrackingNumber('TRK-8K2QX7M4')).toBe(false);
    expect(isWellFormedTrackingNumber('TRK-8K2QX7M4R!')).toBe(false);
    expect(isWellFormedTrackingNumber('')).toBe(false);
  });
});
