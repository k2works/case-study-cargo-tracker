import { BillingValidationError } from './billing-validation-error.js';

/**
 * 予約参照 ID（値オブジェクト）。Booking Context の Cargo との関連識別子。
 * Billing Context 側で他 BC のドメイン型に依存しないための参照専用の識別子。
 */
export class BillingBookingId {
  private constructor(readonly value: string) {}

  static of(value: string): BillingBookingId {
    if (!value || value.trim().length === 0) {
      throw new BillingValidationError('BillingBookingId は必須です');
    }
    return new BillingBookingId(value);
  }

  equals(other: BillingBookingId): boolean {
    return this.value === other.value;
  }
}
