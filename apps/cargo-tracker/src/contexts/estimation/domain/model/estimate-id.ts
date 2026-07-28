import { randomUUID } from 'node:crypto';

/** 見積 ID（UUID ベースの業務識別子） */
export class EstimateId {
  private constructor(readonly value: string) {}

  static generate(): EstimateId {
    return new EstimateId(randomUUID());
  }

  static of(value: string): EstimateId {
    if (!value || value.trim().length === 0) {
      throw new Error('EstimateId は必須です');
    }
    return new EstimateId(value);
  }

  equals(other: EstimateId): boolean {
    return this.value === other.value;
  }
}
