import { Decimal } from 'decimal.js';
import { BillingValidationError } from './billing-validation-error.js';

/**
 * 金額（値オブジェクト）。金額と通貨コードのペア。
 * 端数処理は最小通貨単位（整数）で行い、負値は保持しない。
 * 浮動小数点誤差を避けるため内部表現は decimal.js の Decimal を用いる。
 */
export class Money {
  private constructor(
    readonly amount: Decimal,
    readonly currency: string,
  ) {}

  /**
   * 金額を生成する。value は最小通貨単位に丸める（四捨五入）。
   * 負値・空通貨コードはエラー。
   */
  static of(value: Decimal | number, currency: string): Money {
    const normalizedCurrency = currency.trim().toUpperCase();
    if (normalizedCurrency.length === 0) {
      throw new BillingValidationError('通貨コードは必須です');
    }
    const rounded = new Decimal(value).toDecimalPlaces(0, Decimal.ROUND_HALF_UP);
    if (rounded.isNegative()) {
      throw new BillingValidationError(`金額は負値にできません: ${rounded.toString()}`);
    }
    return new Money(rounded, normalizedCurrency);
  }

  /** 通貨コード指定のゼロ金額 */
  static zero(currency: string): Money {
    return Money.of(0, currency);
  }

  /** 加算（通貨一致を検証） */
  add(other: Money): Money {
    this.assertSameCurrency(other);
    return Money.of(this.amount.plus(other.amount), this.currency);
  }

  /** 減算（通貨一致を検証。結果が負値ならエラー） */
  subtract(other: Money): Money {
    this.assertSameCurrency(other);
    return Money.of(this.amount.minus(other.amount), this.currency);
  }

  /** 係数を乗じる（結果は最小通貨単位に丸める） */
  multiply(factor: Decimal): Money {
    return Money.of(this.amount.times(factor), this.currency);
  }

  equals(other: Money): boolean {
    return this.currency === other.currency && this.amount.equals(other.amount);
  }

  private assertSameCurrency(other: Money): void {
    if (this.currency !== other.currency) {
      throw new BillingValidationError(
        `通貨コードが一致しません: ${this.currency} と ${other.currency}`,
      );
    }
  }
}
