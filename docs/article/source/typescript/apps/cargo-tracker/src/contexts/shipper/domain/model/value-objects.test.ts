import { describe, expect, it } from 'vitest';
import {
  DiscountRate,
  Email,
  ShipperCode,
  ShipperName,
  ShipperType,
} from './value-objects.js';

describe('DiscountRate（割引率 0〜30%）', () => {
  it.each([0, 0.15, 0.3])('境界内 %f を許可する', (rate) => {
    expect(DiscountRate.of(rate).value).toBe(rate);
  });

  it.each([-0.0001, 0.3001, 1])('範囲外 %f は拒否する', (rate) => {
    expect(() => DiscountRate.of(rate)).toThrow();
  });

  it('個人向けのゼロ割引を生成できる', () => {
    expect(DiscountRate.zero().value).toBe(0);
  });
});

describe('Email', () => {
  it('正しい形式を許可する', () => {
    expect(Email.of('user@example.com').value).toBe('user@example.com');
  });

  it.each(['', 'invalid', 'a@b', 'no-at-mark.com'])('不正な形式 "%s" を拒否する', (v) => {
    expect(() => Email.of(v)).toThrow();
  });

  it('前後の空白を除去し小文字化して正規化する', () => {
    expect(Email.of('  User@Example.com ').value).toBe('user@example.com');
  });
});

describe('ShipperCode', () => {
  it('SHP- プレフィックス + 8 文字で自動生成される', () => {
    const code = ShipperCode.generate();
    expect(code.value).toMatch(/^SHP-[0-9a-f]{8}$/);
  });

  it('既存値から再構築できる', () => {
    expect(ShipperCode.of('SHP-abcd1234').value).toBe('SHP-abcd1234');
  });
});

describe('ShipperName', () => {
  it('空文字を拒否する', () => {
    expect(() => ShipperName.of('')).toThrow();
  });
  it('値を保持する', () => {
    expect(ShipperName.of('山田太郎').value).toBe('山田太郎');
  });
});

describe('ShipperType', () => {
  it('INDIVIDUAL / CORPORATE を持つ', () => {
    expect(ShipperType.INDIVIDUAL).toBe('INDIVIDUAL');
    expect(ShipperType.CORPORATE).toBe('CORPORATE');
  });
});
