import { describe, expect, it } from 'vitest';
import { Decimal } from 'decimal.js';
import { Money } from './money.js';
import { BillingValidationError } from './billing-validation-error.js';

describe('Money 値オブジェクト', () => {
  it('金額と通貨コードを保持する', () => {
    const money = Money.of(1000, 'JPY');
    expect(money.amount.toString()).toBe('1000');
    expect(money.currency).toBe('JPY');
  });

  it('通貨コードは大文字に正規化する', () => {
    expect(Money.of(100, 'jpy').currency).toBe('JPY');
  });

  it('zero はゼロ金額を生成する', () => {
    expect(Money.of(0, 'JPY').equals(Money.zero('JPY'))).toBe(true);
  });

  it.each([
    ['端数切り上げ', 100.5, '101'],
    ['端数切り捨て', 100.4, '100'],
    ['整数はそのまま', 250, '250'],
    ['ゼロ', 0, '0'],
  ])('of は最小通貨単位に丸める（%s）', (_label, value, expected) => {
    expect(Money.of(value, 'JPY').amount.toString()).toBe(expected);
  });

  it('負値はエラー', () => {
    expect(() => Money.of(-1, 'JPY')).toThrow(BillingValidationError);
  });

  it('空の通貨コードはエラー', () => {
    expect(() => Money.of(100, '   ')).toThrow(BillingValidationError);
  });

  it('add は同一通貨で加算する', () => {
    expect(Money.of(100, 'JPY').add(Money.of(250, 'JPY')).amount.toString()).toBe('350');
  });

  it('subtract は同一通貨で減算する', () => {
    expect(Money.of(300, 'JPY').subtract(Money.of(100, 'JPY')).amount.toString()).toBe('200');
  });

  it('subtract の結果が負値ならエラー', () => {
    expect(() => Money.of(100, 'JPY').subtract(Money.of(300, 'JPY'))).toThrow(BillingValidationError);
  });

  it('通貨不一致の加算はエラー', () => {
    expect(() => Money.of(100, 'JPY').add(Money.of(100, 'USD'))).toThrow(BillingValidationError);
  });

  it('multiply は係数を乗じて丸める', () => {
    expect(Money.of(1000, 'JPY').multiply(new Decimal('0.9')).amount.toString()).toBe('900');
    expect(Money.of(1001, 'JPY').multiply(new Decimal('0.1')).amount.toString()).toBe('100');
  });
});
