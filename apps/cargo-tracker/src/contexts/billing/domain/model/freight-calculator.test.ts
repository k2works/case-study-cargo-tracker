import { describe, expect, it } from 'vitest';
import { Decimal } from 'decimal.js';
import { FreightCalculator } from './freight-calculator.js';
import { BillingValidationError } from './billing-validation-error.js';

describe('FreightCalculator ドメインサービス', () => {
  const calculator = new FreightCalculator();

  // 距離係数 = transitDays × 100（円/日）、基本料金 = 距離係数 × 重量 × 種別係数
  it.each([
    ['GENERAL（係数 1.0）', 'GENERAL', '2000000'],
    ['HAZARDOUS（係数 1.8）', 'HAZARDOUS', '3600000'],
    ['REFRIGERATED（係数 1.5）', 'REFRIGERATED', '3000000'],
  ])('貨物種別係数を反映する（%s）', (_label, cargoType, expected) => {
    const money = calculator.calculate({
      transitDays: 20,
      weightKg: new Decimal(1000),
      cargoType,
    });
    expect(money.amount.toString()).toBe(expected);
    expect(money.currency).toBe('JPY');
  });

  it('所要日数が 0 以下はエラー', () => {
    expect(() =>
      calculator.calculate({ transitDays: 0, weightKg: new Decimal(1000), cargoType: 'GENERAL' }),
    ).toThrow(BillingValidationError);
  });

  it('重量が 0 以下はエラー', () => {
    expect(() =>
      calculator.calculate({ transitDays: 10, weightKg: new Decimal(0), cargoType: 'GENERAL' }),
    ).toThrow(BillingValidationError);
  });

  it('不正な貨物種別はエラー', () => {
    expect(() =>
      calculator.calculate({ transitDays: 10, weightKg: new Decimal(1000), cargoType: 'UNKNOWN' }),
    ).toThrow(BillingValidationError);
  });
});
