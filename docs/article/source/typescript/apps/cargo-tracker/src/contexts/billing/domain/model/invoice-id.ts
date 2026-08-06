import { randomUUID } from 'node:crypto';
import { BillingValidationError } from './billing-validation-error.js';

/** 請求書 ID（UUID ベースの業務識別子） */
export class InvoiceId {
  private constructor(readonly value: string) {}

  static generate(): InvoiceId {
    return new InvoiceId(randomUUID());
  }

  static of(value: string): InvoiceId {
    if (!value || value.trim().length === 0) {
      throw new BillingValidationError('InvoiceId は必須です');
    }
    return new InvoiceId(value);
  }

  equals(other: InvoiceId): boolean {
    return this.value === other.value;
  }
}
