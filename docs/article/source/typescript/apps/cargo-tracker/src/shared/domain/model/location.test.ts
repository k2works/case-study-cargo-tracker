import { describe, expect, it } from 'vitest';
import { Location } from './location.js';

describe('Location（UN/LOCODE 値オブジェクト）', () => {
  it('5 文字の UN/LOCODE を許可し大文字化する', () => {
    expect(Location.of('jptyo').unlocode).toBe('JPTYO');
  });

  it.each(['', 'JPT', 'JPTYOO', 'JP-YO'])('不正な形式 "%s" を拒否する', (v) => {
    expect(() => Location.of(v)).toThrow();
  });

  it('sameAs で同一地点を判定する', () => {
    expect(Location.of('JPTYO').sameAs(Location.of('jptyo'))).toBe(true);
    expect(Location.of('JPTYO').sameAs(Location.of('USLAX'))).toBe(false);
  });
});
