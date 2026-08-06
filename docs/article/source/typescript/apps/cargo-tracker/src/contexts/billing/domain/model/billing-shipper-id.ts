import { BillingValidationError } from './billing-validation-error.js';

const CORPORATE = 'CORPORATE';
const INDIVIDUAL = 'INDIVIDUAL';

/**
 * 荷主参照 ID（値オブジェクト）。荷主識別子と荷主種別を保持し、法人判定（isCorporate）を内包する。
 * 割引適用の可否判定はこの isCorporate() を根拠とする。
 */
export class BillingShipperId {
  private constructor(
    readonly value: string,
    readonly shipperType: string,
  ) {}

  static of(value: string, shipperType: string): BillingShipperId {
    if (!value || value.trim().length === 0) {
      throw new BillingValidationError('BillingShipperId は必須です');
    }
    const normalizedType = shipperType.trim().toUpperCase();
    if (normalizedType !== CORPORATE && normalizedType !== INDIVIDUAL) {
      throw new BillingValidationError(`不正な荷主種別: ${shipperType}`);
    }
    return new BillingShipperId(value, normalizedType);
  }

  /** 法人荷主か否か。割引適用対象の判定に用いる */
  isCorporate(): boolean {
    return this.shipperType === CORPORATE;
  }

  equals(other: BillingShipperId): boolean {
    return this.value === other.value && this.shipperType === other.shipperType;
  }
}
