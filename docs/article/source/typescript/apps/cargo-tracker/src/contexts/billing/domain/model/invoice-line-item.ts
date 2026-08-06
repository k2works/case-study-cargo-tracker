import { Money } from './money.js';
import { BillingValidationError } from './billing-validation-error.js';

/**
 * 請求明細（エンティティ）。基本料金・消費税・割引根拠・例外調整など、
 * 請求金額の内訳を人間可読な説明つきで表現する。
 */
export class InvoiceLineItem {
  private constructor(
    readonly description: string,
    readonly amount: Money,
  ) {}

  static of(description: string, amount: Money): InvoiceLineItem {
    const trimmed = description.trim();
    if (trimmed.length === 0) {
      throw new BillingValidationError('明細の説明は必須です');
    }
    if (trimmed.length > 200) {
      throw new BillingValidationError('明細の説明は 200 文字以内です');
    }
    return new InvoiceLineItem(trimmed, amount);
  }
}
