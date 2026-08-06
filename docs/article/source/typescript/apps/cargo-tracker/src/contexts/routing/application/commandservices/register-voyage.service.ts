import type { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { CarrierMovement, Schedule, Voyage } from '../../domain/model/voyage.js';
import type { VoyageRepository } from '../../domain/repository/voyage-repository.js';

interface CarrierMovementCommand {
  departureLocation: string;
  arrivalLocation: string;
  departureTime: Date;
  arrivalTime: Date;
}

export interface RegisterVoyageCommand {
  voyageNumber: string;
  shipName: string;
  carrierName: string;
  supportedCargoTypes: CargoType[];
  carrierMovements: CarrierMovementCommand[];
}

export interface RegisterVoyageResult {
  id: number;
  voyageNumber: string;
}

export class RegisterVoyageService {
  constructor(private readonly voyages: VoyageRepository) {}

  async register(command: RegisterVoyageCommand): Promise<RegisterVoyageResult> {
    const voyage = Voyage.register({
      voyageNumber: command.voyageNumber,
      shipName: command.shipName,
      carrierName: command.carrierName,
      supportedCargoTypes: command.supportedCargoTypes,
      schedule: toSchedule(command.carrierMovements),
    });
    const id = await this.voyages.save(voyage);
    return { id, voyageNumber: voyage.voyageNumber.value };
  }
}

export function toSchedule(carrierMovements: CarrierMovementCommand[]): Schedule {
  return Schedule.of(
    carrierMovements.map((movement) =>
      CarrierMovement.of({
        departureLocation: movement.departureLocation,
        arrivalLocation: movement.arrivalLocation,
        departureTime: movement.departureTime,
        arrivalTime: movement.arrivalTime,
      }),
    ),
  );
}
