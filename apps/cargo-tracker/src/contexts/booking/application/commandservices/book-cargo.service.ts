import type { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from '../../domain/model/cargo.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import {
  CARGO_BOOKED_EVENT,
  CargoBookedEvent,
} from '../../domain/event/cargo-booked-event.js';
import {
  ShipperNotFoundError,
  type ShipperExistenceChecker,
} from '../outboundservices/acl/shipper-existence-checker.js';

/** ドメインイベント発行ポート（インフラの EventEmitter2 アダプタが実装） */
export interface EventPublisher {
  emit(event: string, payload: unknown): void;
}

export interface BookCargoCommand {
  shipperCode: string;
  cargoType: CargoType;
  weightKg: number;
  origin: string;
  destination: string;
  arrivalDeadline: Date;
  consignee: { name: string; address: string; contactEmail: string };
  dimensions?: { length: number; width: number; height: number };
  quantity?: number;
  description?: string;
  hazardous?: { hazardousClass: string; unNumber: string; properShippingName: string };
  temperature?: { minTemperature: number; maxTemperature: number; unit: string };
}

export interface BookCargoResult {
  bookingId: string;
}

/**
 * 貨物予約登録ユースケース（US04/US05）。
 * ShipperExistenceChecker ACL で荷主の存在を確認し（BC 独立性）、
 * 仮受付（PRELIMINARY）で登録後に CargoBookedEvent を発行する。
 */
export class BookCargoService {
  constructor(
    private readonly cargos: CargoRepository,
    private readonly shipperChecker: ShipperExistenceChecker,
    private readonly events: EventPublisher,
  ) {}

  async book(command: BookCargoCommand): Promise<BookCargoResult> {
    const shipperId = await this.shipperChecker.resolveShipperId(command.shipperCode);
    if (shipperId === null) {
      throw new ShipperNotFoundError(command.shipperCode);
    }

    const cargo = Cargo.book({
      shipperId,
      cargoType: command.cargoType,
      weightKg: command.weightKg,
      origin: command.origin,
      destination: command.destination,
      arrivalDeadline: command.arrivalDeadline,
      consignee: command.consignee,
      dimensions: command.dimensions,
      quantity: command.quantity,
      description: command.description,
      hazardous: command.hazardous,
      temperature: command.temperature,
    });

    await this.cargos.save(cargo);

    // 永続化後にイベントを発行する（ADR-005: コミット後発行）
    this.events.emit(
      CARGO_BOOKED_EVENT,
      new CargoBookedEvent(
        cargo.bookingId.value,
        shipperId,
        cargo.routeSpecification.origin.unlocode,
        cargo.routeSpecification.destination.unlocode,
      ),
    );

    return { bookingId: cargo.bookingId.value };
  }
}
