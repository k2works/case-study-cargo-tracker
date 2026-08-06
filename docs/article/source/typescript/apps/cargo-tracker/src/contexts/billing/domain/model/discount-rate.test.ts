import { describe, expect, it } from 'vitest';
import { Decimal } from 'decimal.js';
import { DiscountRate } from './discount-rate.js';
import { Money } from './money.js';
import { BillingValidationError } from './billing-validation-error.js';

describe('DiscountRate 値オブジェクト', () => {
  it.each([
    ['0%', 0],
    ['15%', 0.15],
    ['30%（上限）', 0.3],
  ])('範囲内の割引率を生成できる（%s）', (_label, rate) => {
    expect(DiscountRate.of(rate).rate.toNumber()).toBe(rate);
  });

  it.each([
    ['上限超過', 0.3001],
    ['負値', -0.01],
  ])('範囲外はエラー（%s）', (_label, rate) => {
    expect(() => DiscountRate.of(rate)).toThrow(BillingValidationError);
  });

  it('applyTo は割引後金額（base × (1 - rate)）を返す', () => {
    const base = Money.of(1000, 'JPY');
    expect(DiscountRate.of(0.3).applyTo(base).amount.toString()).toBe('700');
    expect(DiscountRate.zero().applyTo(base).amount.toString()).toBe('1000');
  });

  it('discountAmount は割引額（base × rate）を返す', () => {
    expect(DiscountRate.of(0.3).discountAmount(Money.of(1000, 'JPY')).amount.toString()).toBe('300');
  });

  it('Decimal でも生成できる', () => {
    expect(DiscountRate.of(new Decimal('0.1')).rate.toString()).toBe('0.1');
  });
});
