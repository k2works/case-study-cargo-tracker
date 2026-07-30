import { HandlingValidationError } from './handling-validation-error.js';

/**
 * 荷役作業に紐づく航海番号（Handling Context 固有型）。
 * Routing の VoyageNumber とは共有せず、ACL 変換の境界とする（domain-model の VoyageNumber 分離設計）。
 */
export class HandlingVoyageNumber {
  private constructor(readonly value: string) {}

  static of(raw: string): HandlingVoyageNumber {
    const normalized = raw.trim();
    if (normalized.length === 0 || normalized.length > 20) {
      throw new HandlingValidationError(`不正な航海番号: ${raw}`);
    }
    return new HandlingVoyageNumber(normalized);
  }

  equals(other: HandlingVoyageNumber): boolean {
    return this.value === other.value;
  }
}
