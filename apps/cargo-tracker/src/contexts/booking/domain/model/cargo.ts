import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { BookingStatus } from './booking-status.js';
import { CargoItinerary } from './cargo-itinerary.js';
import { BookingValidationError } from './booking-validation-error.js';
import {
  BookingId,
  Consignee,
  Dimensions,
  HazardousDeclaration,
  RouteSpecification,
  TemperatureRequirement,
  Weight,
} from './value-objects.js';

interface ConsigneeInput {
  name: string;
  address: string;
  contactEmail: string;
}

interface BookCargoParams {
  shipperId: number;
  cargoType: CargoType;
  weightKg: number;
  origin: string;
  destination: string;
  arrivalDeadline: Date;
  consignee: ConsigneeInput;
  dimensions?: { length: number; width: number; height: number };
  quantity?: number;
  description?: string;
  hazardous?: { hazardousClass: string; unNumber: string; properShippingName: string };
  temperature?: { minTemperature: number; maxTemperature: number; unit: string };
}

interface ReconstructParams extends Omit<BookCargoParams, 'consignee'> {
  id: number;
  bookingId: string;
  bookingStatus: BookingStatus;
  consignee?: ConsigneeInput | null;
  cargoItinerary?: CargoItinerary | null;
  trackingNumber?: string | null;
}

/**
 * 貨物集約ルート（Booking Context）。予約の状態遷移・貨物仕様・荷受人を統括する。
 * ビジネスルール: HAZARDOUS は危険物申告必須・REFRIGERATED は温度管理条件必須・
 * 出発地≠目的地・重量>0（domain-model）。
 */
export class Cargo {
  private constructor(
    readonly id: number | undefined,
    readonly bookingId: BookingId,
    readonly shipperId: number,
    readonly cargoType: CargoType,
    readonly weight: Weight,
    readonly routeSpecification: RouteSpecification,
    readonly consignee: Consignee,
    private _bookingStatus: BookingStatus,
    readonly dimensions: Dimensions | undefined,
    readonly quantity: number | undefined,
    readonly description: string | undefined,
    readonly hazardousDeclaration: HazardousDeclaration | undefined,
    readonly temperatureRequirement: TemperatureRequirement | undefined,
    private _cargoItinerary: CargoItinerary | undefined,
    private _trackingNumber: string | undefined,
  ) {}

  get bookingStatus(): BookingStatus {
    return this._bookingStatus;
  }

  get cargoItinerary(): CargoItinerary | undefined {
    return this._cargoItinerary;
  }

  get trackingNumber(): string | undefined {
    return this._trackingNumber;
  }

  static book(params: BookCargoParams): Cargo {
    const hazardous = Cargo.resolveHazardous(params);
    const temperature = Cargo.resolveTemperature(params);
    return new Cargo(
      undefined,
      BookingId.generate(),
      params.shipperId,
      params.cargoType,
      Weight.of(params.weightKg),
      RouteSpecification.of({
        origin: params.origin,
        destination: params.destination,
        arrivalDeadline: params.arrivalDeadline,
      }),
      Consignee.of(params.consignee),
      BookingStatus.PRELIMINARY,
      params.dimensions ? Dimensions.of(params.dimensions) : undefined,
      params.quantity,
      params.description,
      hazardous,
      temperature,
      undefined,
      undefined,
    );
  }

  static reconstruct(params: ReconstructParams): Cargo {
    return new Cargo(
      params.id,
      BookingId.of(params.bookingId),
      params.shipperId,
      params.cargoType,
      Weight.of(params.weightKg),
      RouteSpecification.of({
        origin: params.origin,
        destination: params.destination,
        arrivalDeadline: params.arrivalDeadline,
      }),
      // 荷受人は IT2 以降の予約では必須。null になるのは荷受人カラム追加前の
      // 旧データ復元時のみで、そのための後方互換フォールバック。
      Consignee.of(
        params.consignee ?? { name: '(不明)', address: '', contactEmail: 'unknown@example.com' },
      ),
      params.bookingStatus,
      params.dimensions ? Dimensions.of(params.dimensions) : undefined,
      params.quantity,
      params.description,
      Cargo.resolveHazardous(params),
      Cargo.resolveTemperature(params),
      params.cargoItinerary ?? undefined,
      params.trackingNumber ?? undefined,
    );
  }

  /** 経路設計者へ引き渡す（US06、PRELIMINARY → ROUTING_IN_PROGRESS） */
  assignToRouting(): void {
    if (this._bookingStatus !== BookingStatus.PRELIMINARY) {
      throw new BookingValidationError(
        `仮受付（PRELIMINARY）の予約のみ引き渡せます（現在: ${this._bookingStatus}）`,
      );
    }
    this._bookingStatus = BookingStatus.ROUTING_IN_PROGRESS;
  }

  /** 経路（CargoItinerary）を紐付ける（US11、ROUTING_IN_PROGRESS → ROUTE_PROPOSED） */
  assignRoute(itinerary: CargoItinerary): void {
    if (this._bookingStatus !== BookingStatus.ROUTING_IN_PROGRESS) {
      throw new BookingValidationError(
        `経路設計中（ROUTING_IN_PROGRESS）の予約のみ経路を紐付けできます（現在: ${this._bookingStatus}）`,
      );
    }
    // ドメイン不変条件: 旅程はルート仕様（出発地・目的地・到着期限）を満たす必要がある。
    // これにより、Presentation 層でクライアント値が偽装されても不正な経路確定を防ぐ。
    const legs = itinerary.legs;
    if (!legs[0].loadLocation.sameAs(this.routeSpecification.origin)) {
      throw new BookingValidationError('旅程の出発地がルート仕様の出発地と一致しません');
    }
    if (!legs.at(-1)!.unloadLocation.sameAs(this.routeSpecification.destination)) {
      throw new BookingValidationError('旅程の目的地がルート仕様の目的地と一致しません');
    }
    if (!isSameOrBeforeDate(itinerary.expectedArrivalTime(), this.routeSpecification.arrivalDeadline)) {
      throw new BookingValidationError('旅程の到着予定が到着期限を超えています');
    }
    this._cargoItinerary = itinerary;
    this._bookingStatus = BookingStatus.ROUTE_PROPOSED;
  }

  /** 予約を確定する（US13、ROUTE_PROPOSED → CONFIRMED） */
  confirm(): void {
    if (this._bookingStatus !== BookingStatus.ROUTE_PROPOSED) {
      throw new BookingValidationError(
        `経路提案中（ROUTE_PROPOSED）の予約のみ確定できます（現在: ${this._bookingStatus}）`,
      );
    }
    this._bookingStatus = BookingStatus.CONFIRMED;
  }

  /** 経路設計へ差し戻す（US13 ルート変更希望、ROUTE_PROPOSED → ROUTING_IN_PROGRESS） */
  returnToRouting(): void {
    if (this._bookingStatus !== BookingStatus.ROUTE_PROPOSED) {
      throw new BookingValidationError(
        `経路提案中（ROUTE_PROPOSED）の予約のみ差し戻せます（現在: ${this._bookingStatus}）`,
      );
    }
    this._bookingStatus = BookingStatus.ROUTING_IN_PROGRESS;
  }

  /** 追跡番号を発行する（US14、CONFIRMED → TRACKING_ISSUED） */
  issueTracking(trackingNumber: string): void {
    if (this._bookingStatus !== BookingStatus.CONFIRMED) {
      throw new BookingValidationError(
        `確定済み（CONFIRMED）の予約のみ追跡番号を発行できます（現在: ${this._bookingStatus}）`,
      );
    }
    const value = trackingNumber.trim();
    if (value.length === 0) {
      throw new BookingValidationError('追跡番号は必須です');
    }
    this._trackingNumber = value;
    this._bookingStatus = BookingStatus.TRACKING_ISSUED;
  }

  /** 予約をキャンセルする（任意の状態から CANCELLED。ただし CANCELLED からは不可） */
  cancel(): void {
    if (this._bookingStatus === BookingStatus.CANCELLED) {
      throw new BookingValidationError('既にキャンセル済みです');
    }
    this._bookingStatus = BookingStatus.CANCELLED;
  }

  private static resolveHazardous(
    params: Pick<BookCargoParams, 'cargoType' | 'hazardous'>,
  ): HazardousDeclaration | undefined {
    if (params.cargoType === CargoType.HAZARDOUS) {
      if (!params.hazardous) {
        throw new BookingValidationError('危険物には危険物申告が必須です');
      }
      return HazardousDeclaration.of(params.hazardous);
    }
    return undefined;
  }

  private static resolveTemperature(
    params: Pick<BookCargoParams, 'cargoType' | 'temperature'>,
  ): TemperatureRequirement | undefined {
    if (params.cargoType === CargoType.REFRIGERATED) {
      if (!params.temperature) {
        throw new BookingValidationError('冷凍・冷蔵貨物には温度管理条件が必須です');
      }
      return TemperatureRequirement.of(params.temperature);
    }
    return undefined;
  }
}

/** 期限判定は日付単位（当日着は期限内）。DATE 期限 vs TIMESTAMP 到着の当日着刈り取りを防ぐ */
function isSameOrBeforeDate(target: Date, deadline: Date): boolean {
  const t = Date.UTC(target.getUTCFullYear(), target.getUTCMonth(), target.getUTCDate());
  const d = Date.UTC(deadline.getUTCFullYear(), deadline.getUTCMonth(), deadline.getUTCDate());
  return t <= d;
}
