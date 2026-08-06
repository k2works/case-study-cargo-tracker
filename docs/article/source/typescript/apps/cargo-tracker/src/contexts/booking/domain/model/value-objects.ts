import { randomUUID } from 'node:crypto';
import { Location } from '../../../../shared/domain/model/location.js';
import { isValidEmail } from '../../../../shared/domain/model/email-validation.js';
import { BookingValidationError } from './booking-validation-error.js';

/** 予約 ID（UUID ベースの業務識別子） */
export class BookingId {
  private constructor(readonly value: string) {}
  static generate(): BookingId {
    return new BookingId(randomUUID());
  }
  static of(value: string): BookingId {
    if (!value || value.trim().length === 0) {
      throw new BookingValidationError('BookingId は必須です');
    }
    return new BookingId(value);
  }
  equals(other: BookingId): boolean {
    return this.value === other.value;
  }
}

/** 荷受人（氏名・住所・連絡先メール） */
export class Consignee {
  private constructor(
    readonly name: string,
    readonly address: string,
    readonly contactEmail: string,
  ) {}

  static of(props: { name: string; address: string; contactEmail: string }): Consignee {
    const name = props.name.trim();
    if (name.length === 0) {
      throw new BookingValidationError('荷受人氏名は必須です');
    }
    const email = props.contactEmail.trim().toLowerCase();
    if (!isValidEmail(email)) {
      throw new BookingValidationError(`荷受人の連絡先メールが不正です: ${props.contactEmail}`);
    }
    return new Consignee(name, props.address.trim(), email);
  }
}

/** ルート仕様（出発地・目的地・到着期限）。出発地と目的地は異なる */
export class RouteSpecification {
  private constructor(
    readonly origin: Location,
    readonly destination: Location,
    readonly arrivalDeadline: Date,
  ) {}

  static of(props: { origin: string; destination: string; arrivalDeadline: Date }): RouteSpecification {
    // Location の検証エラー（SharedValidationError）を Booking の検証エラーへ翻訳し、
    // 不正な UN/LOCODE 入力が内部障害として誤分類されないようにする
    let origin: Location;
    let destination: Location;
    try {
      origin = Location.of(props.origin);
      destination = Location.of(props.destination);
    } catch (error) {
      throw new BookingValidationError(
        error instanceof Error ? error.message : '出発地・目的地の指定が不正です',
      );
    }
    if (origin.sameAs(destination)) {
      throw new BookingValidationError('出発地と目的地は異なる必要があります');
    }
    return new RouteSpecification(origin, destination, props.arrivalDeadline);
  }
}

/** 重量（正の値） */
export class Weight {
  private constructor(readonly value: number) {}
  static of(value: number): Weight {
    if (Number.isNaN(value) || value <= 0) {
      throw new BookingValidationError(`重量は正の値が必要です: ${value}`);
    }
    return new Weight(value);
  }
}

/** 寸法（長さ・幅・高さ、オプション） */
export class Dimensions {
  private constructor(
    readonly length: number,
    readonly width: number,
    readonly height: number,
  ) {}
  static of(props: { length: number; width: number; height: number }): Dimensions {
    for (const [k, v] of Object.entries(props)) {
      if (Number.isNaN(v) || v < 0) {
        throw new BookingValidationError(`寸法（${k}）は 0 以上が必要です`);
      }
    }
    return new Dimensions(props.length, props.width, props.height);
  }
}

/** 危険物申告（クラス・UN 番号・正式輸送品名、すべて必須） */
export class HazardousDeclaration {
  private constructor(
    readonly hazardousClass: string,
    readonly unNumber: string,
    readonly properShippingName: string,
  ) {}

  static of(props: { hazardousClass: string; unNumber: string; properShippingName: string }): HazardousDeclaration {
    const hazardousClass = props.hazardousClass.trim();
    const unNumber = props.unNumber.trim();
    const properShippingName = props.properShippingName.trim();
    if (hazardousClass.length === 0 || unNumber.length === 0 || properShippingName.length === 0) {
      throw new BookingValidationError('危険物申告（クラス・UN 番号・正式輸送品名）はすべて必須です');
    }
    return new HazardousDeclaration(hazardousClass, unNumber, properShippingName);
  }
}

export type TemperatureUnit = 'CELSIUS' | 'FAHRENHEIT';

/** 温度管理条件（最低・最高温度・単位） */
export class TemperatureRequirement {
  private constructor(
    readonly minTemperature: number,
    readonly maxTemperature: number,
    readonly unit: TemperatureUnit,
  ) {}

  static of(props: { minTemperature: number; maxTemperature: number; unit: string }): TemperatureRequirement {
    if (Number.isNaN(props.minTemperature) || Number.isNaN(props.maxTemperature)) {
      throw new BookingValidationError('温度は数値で入力してください');
    }
    if (props.minTemperature > props.maxTemperature) {
      throw new BookingValidationError('最低温度は最高温度以下である必要があります');
    }
    const unit: TemperatureUnit = props.unit === 'FAHRENHEIT' ? 'FAHRENHEIT' : 'CELSIUS';
    return new TemperatureRequirement(props.minTemperature, props.maxTemperature, unit);
  }
}
