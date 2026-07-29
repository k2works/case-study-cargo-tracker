import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Location } from '../../../../shared/domain/model/location.js';
import { RoutingValidationError } from './routing-validation-error.js';

export class VoyageNumber {
  private constructor(readonly value: string) {}

  static of(value: string): VoyageNumber {
    const normalized = value.trim().toUpperCase();
    if (normalized.length === 0) {
      throw new RoutingValidationError('航海番号は必須です');
    }
    return new VoyageNumber(normalized);
  }

  equals(other: VoyageNumber): boolean {
    return this.value === other.value;
  }
}

interface CarrierMovementParams {
  departureLocation: string;
  arrivalLocation: string;
  departureTime: Date;
  arrivalTime: Date;
}

export class CarrierMovement {
  private constructor(
    readonly departureLocation: Location,
    readonly arrivalLocation: Location,
    readonly departureTime: Date,
    readonly arrivalTime: Date,
    readonly seqNumber: number | undefined,
  ) {}

  static of(params: CarrierMovementParams): CarrierMovement {
    const departureLocation = Location.of(params.departureLocation);
    const arrivalLocation = Location.of(params.arrivalLocation);
    if (departureLocation.sameAs(arrivalLocation)) {
      throw new RoutingValidationError('出発港と到着港は異なる必要があります');
    }
    if (params.departureTime.getTime() > params.arrivalTime.getTime()) {
      throw new RoutingValidationError('出発時刻は到着時刻以前である必要があります');
    }
    return new CarrierMovement(
      departureLocation,
      arrivalLocation,
      new Date(params.departureTime),
      new Date(params.arrivalTime),
      undefined,
    );
  }

  withSeqNumber(seqNumber: number): CarrierMovement {
    return new CarrierMovement(
      this.departureLocation,
      this.arrivalLocation,
      this.departureTime,
      this.arrivalTime,
      seqNumber,
    );
  }
}

export class Schedule {
  private constructor(readonly carrierMovements: CarrierMovement[]) {}

  static of(carrierMovements: CarrierMovement[]): Schedule {
    if (carrierMovements.length === 0) {
      throw new RoutingValidationError('運送区間は 1 件以上必要です');
    }
    for (let index = 1; index < carrierMovements.length; index += 1) {
      const previous = carrierMovements[index - 1];
      const current = carrierMovements[index];
      if (!previous.arrivalLocation.sameAs(current.departureLocation)) {
        throw new RoutingValidationError('運送区間は到着港から次の出発港へ接続する必要があります');
      }
      if (current.departureTime.getTime() < previous.arrivalTime.getTime()) {
        throw new RoutingValidationError('次の運送区間は前区間の到着以降に出発する必要があります');
      }
    }
    return new Schedule(
      carrierMovements.map((carrierMovement, index) => carrierMovement.withSeqNumber(index + 1)),
    );
  }
}

interface RegisterVoyageParams {
  voyageNumber: string;
  shipName: string;
  carrierName: string;
  supportedCargoTypes: CargoType[];
  schedule: Schedule;
}

interface ReconstructVoyageParams extends RegisterVoyageParams {
  id: number;
}

export class Voyage {
  private constructor(
    readonly id: number | undefined,
    readonly voyageNumber: VoyageNumber,
    readonly shipName: string,
    readonly carrierName: string,
    readonly supportedCargoTypes: CargoType[],
    readonly schedule: Schedule,
  ) {}

  static register(params: RegisterVoyageParams): Voyage {
    const shipName = params.shipName.trim();
    const carrierName = params.carrierName.trim();
    if (shipName.length === 0) {
      throw new RoutingValidationError('船名は必須です');
    }
    if (carrierName.length === 0) {
      throw new RoutingValidationError('運送会社は必須です');
    }
    if (params.supportedCargoTypes.length === 0) {
      throw new RoutingValidationError('対応貨物種別は 1 件以上必要です');
    }
    return new Voyage(
      undefined,
      VoyageNumber.of(params.voyageNumber),
      shipName,
      carrierName,
      [...new Set(params.supportedCargoTypes)],
      params.schedule,
    );
  }

  static reconstruct(params: ReconstructVoyageParams): Voyage {
    const voyage = Voyage.register(params);
    return new Voyage(
      params.id,
      voyage.voyageNumber,
      voyage.shipName,
      voyage.carrierName,
      voyage.supportedCargoTypes,
      voyage.schedule,
    );
  }

  supports(cargoType: CargoType): boolean {
    return this.supportedCargoTypes.includes(cargoType);
  }

  changeSchedule(schedule: Schedule): Voyage {
    return new Voyage(
      this.id,
      this.voyageNumber,
      this.shipName,
      this.carrierName,
      this.supportedCargoTypes,
      schedule,
    );
  }

  departureTime(location: string): Date | undefined {
    const target = Location.of(location);
    return this.schedule.carrierMovements.find((movement) =>
      movement.departureLocation.sameAs(target),
    )?.departureTime;
  }

  arrivalTime(location: string): Date | undefined {
    const target = Location.of(location);
    return this.schedule.carrierMovements.find((movement) =>
      movement.arrivalLocation.sameAs(target),
    )?.arrivalTime;
  }
}
