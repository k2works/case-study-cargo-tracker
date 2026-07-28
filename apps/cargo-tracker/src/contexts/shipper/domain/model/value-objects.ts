import { randomUUID } from 'node:crypto';

/** 荷主種別 */
export const ShipperType = {
  INDIVIDUAL: 'INDIVIDUAL',
  CORPORATE: 'CORPORATE',
} as const;
export type ShipperType = (typeof ShipperType)[keyof typeof ShipperType];

export function isShipperType(value: string): value is ShipperType {
  return value === ShipperType.INDIVIDUAL || value === ShipperType.CORPORATE;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** メールアドレス（正規化・形式検証つき） */
export class Email {
  private constructor(readonly value: string) {}

  static of(raw: string): Email {
    const normalized = raw.trim().toLowerCase();
    if (!EMAIL_PATTERN.test(normalized)) {
      throw new Error(`不正なメールアドレス: ${raw}`);
    }
    return new Email(normalized);
  }

  equals(other: Email): boolean {
    return this.value === other.value;
  }
}

/** 荷主コード（SHP- + UUID 先頭 8 文字） */
export class ShipperCode {
  private constructor(readonly value: string) {}

  static generate(): ShipperCode {
    return new ShipperCode(`SHP-${randomUUID().replace(/-/g, '').slice(0, 8)}`);
  }

  static of(value: string): ShipperCode {
    if (!/^SHP-.+/.test(value)) {
      throw new Error(`不正な荷主コード: ${value}`);
    }
    return new ShipperCode(value);
  }
}

/** 荷主名 */
export class ShipperName {
  private constructor(readonly value: string) {}

  static of(raw: string): ShipperName {
    const trimmed = raw.trim();
    if (trimmed.length === 0) {
      throw new Error('荷主名は必須です');
    }
    if (trimmed.length > 200) {
      throw new Error('荷主名は 200 文字以内です');
    }
    return new ShipperName(trimmed);
  }
}

/** 電話番号（オプション） */
export class Phone {
  private constructor(readonly value: string) {}

  static of(raw: string): Phone {
    const trimmed = raw.trim();
    if (trimmed.length === 0) {
      throw new Error('電話番号が空です');
    }
    return new Phone(trimmed);
  }
}

/** 住所（オプション、最大 500 文字） */
export class Address {
  private constructor(readonly value: string) {}

  static of(raw: string): Address {
    const trimmed = raw.trim();
    if (trimmed.length > 500) {
      throw new Error('住所は 500 文字以内です');
    }
    return new Address(trimmed);
  }
}

/** 契約番号（法人のみ） */
export class ContractNumber {
  private constructor(readonly value: string) {}

  static of(raw: string): ContractNumber {
    const trimmed = raw.trim();
    if (trimmed.length === 0) {
      throw new Error('契約番号は必須です');
    }
    return new ContractNumber(trimmed);
  }
}

const MIN_DISCOUNT = 0;
const MAX_DISCOUNT = 0.3;

/** 割引率（0.0000〜0.3000、最大 30%） */
export class DiscountRate {
  private constructor(readonly value: number) {}

  static of(rate: number): DiscountRate {
    if (Number.isNaN(rate) || rate < MIN_DISCOUNT || rate > MAX_DISCOUNT) {
      throw new Error(`割引率は ${MIN_DISCOUNT}〜${MAX_DISCOUNT} の範囲です: ${rate}`);
    }
    return new DiscountRate(rate);
  }

  static zero(): DiscountRate {
    return new DiscountRate(0);
  }
}
