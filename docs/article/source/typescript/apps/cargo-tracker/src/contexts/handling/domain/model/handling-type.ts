import { HandlingValidationError } from './handling-validation-error.js';

const TYPES = ['RECEIVE', 'LOAD', 'UNLOAD', 'CUSTOMS', 'CLAIM'] as const;
type HandlingTypeValue = (typeof TYPES)[number];

/**
 * 荷役種別（値オブジェクト）。RECEIVE / LOAD / UNLOAD / CUSTOMS / CLAIM。
 * VoyageNumber 必須判定と MISROUTED 判定条件（デシジョンテーブル）を内包する。
 */
export class HandlingType {
  private constructor(readonly value: HandlingTypeValue) {}

  static of(raw: string): HandlingType {
    if (!TYPES.includes(raw as HandlingTypeValue)) {
      throw new HandlingValidationError(`不正な荷役種別: ${raw}`);
    }
    return new HandlingType(raw as HandlingTypeValue);
  }

  /** LOAD / UNLOAD は対象航海の特定に VoyageNumber が必須 */
  requiresVoyageNumber(): boolean {
    return this.value === 'LOAD' || this.value === 'UNLOAD';
  }

  /** 場所不一致時に MISROUTED を確定させる種別（LOAD / UNLOAD）。それ以外は警告扱い */
  misroutesOnMismatch(): boolean {
    return this.value === 'LOAD' || this.value === 'UNLOAD';
  }

  isLoadType(): boolean {
    return this.value === 'LOAD';
  }

  isClaimType(): boolean {
    return this.value === 'CLAIM';
  }

  equals(other: HandlingType): boolean {
    return this.value === other.value;
  }
}

export const HANDLING_TYPE_LABELS: Record<HandlingTypeValue, string> = {
  RECEIVE: '受領',
  LOAD: '積込',
  UNLOAD: '荷降し',
  CUSTOMS: '通関',
  CLAIM: '引取',
};
