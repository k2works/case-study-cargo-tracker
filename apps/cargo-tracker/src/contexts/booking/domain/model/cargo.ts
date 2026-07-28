import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { BookingStatus } from './booking-status.js';
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
  ) {}

  get bookingStatus(): BookingStatus {
    return this._bookingStatus;
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
      Consignee.of(
        params.consignee ?? { name: '(不明)', address: '', contactEmail: 'unknown@example.com' },
      ),
      params.bookingStatus,
      params.dimensions ? Dimensions.of(params.dimensions) : undefined,
      params.quantity,
      params.description,
      Cargo.resolveHazardous(params),
      Cargo.resolveTemperature(params),
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
