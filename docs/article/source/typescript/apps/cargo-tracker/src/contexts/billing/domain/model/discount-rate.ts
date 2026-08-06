import { Decimal } from 'decimal.js';
import { Money } from './money.js';
import { BillingValidationError } from './billing-validation-error.js';

const MIN_RATE = new Decimal('0.0000');
const MAX_RATE = new Decimal('0.3000');

/**
 * 割引率（値オブジェクト）。法人契約に基づく割引率（0.0000〜0.3000、最大 30%）。
 * Billing Context 固有の型で、Shipper Context の DiscountRate とは独立に定義する（BC 独立性）。
 */
export class DiscountRate {
  private constructor(readonly rate: Decimal) {}

  static of(rate: Decimal | number): DiscountRate {
    const value = new Decimal(rate);
    if (value.lessThan(MIN_RATE) || value.greaterThan(MAX_RATE)) {
      throw new BillingValidationError(
        `割引率は ${MIN_RATE.toString()}〜${MAX_RATE.toString()} の範囲です: ${value.toString()}`,
      );
    }
    return new DiscountRate(value);
  }

  static zero(): DiscountRate {
    return new DiscountRate(new Decimal(0));
  }

  /** 基準額に割引を適用した後の金額（base × (1 - rate)）を返す */
  applyTo(base: Money): Money {
    return base.multiply(new Decimal(1).minus(this.rate));
  }

  /** 割引額そのもの（base × rate）を返す */
  discountAmount(base: Money): Money {
    return base.multiply(this.rate);
  }

  equals(other: DiscountRate): boolean {
    return this.rate.equals(other.rate);
  }
}
