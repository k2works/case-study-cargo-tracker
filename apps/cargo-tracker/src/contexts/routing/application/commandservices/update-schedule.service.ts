import type { VoyageRepository } from '../../domain/repository/voyage-repository.js';
import { toSchedule, type RegisterVoyageCommand } from './register-voyage.service.js';

export class VoyageNotFoundError extends Error {
  constructor(voyageNumber: string) {
    super(`航海が見つかりません: ${voyageNumber}`);
    this.name = 'VoyageNotFoundError';
  }
}

export type UpdateScheduleCommand = Pick<RegisterVoyageCommand, 'voyageNumber' | 'carrierMovements'>;

export interface UpdateScheduleResult {
  voyageNumber: string;
  previousMovementCount: number;
  updatedMovementCount: number;
}

export class UpdateScheduleService {
  constructor(private readonly voyages: VoyageRepository) {}

  async update(command: UpdateScheduleCommand): Promise<UpdateScheduleResult> {
    const voyage = await this.voyages.findByVoyageNumber(command.voyageNumber);
    if (voyage === null) {
      throw new VoyageNotFoundError(command.voyageNumber);
    }

    const previousMovementCount = voyage.schedule.carrierMovements.length;
    const updated = voyage.changeSchedule(toSchedule(command.carrierMovements));
    await this.voyages.update(updated);

    return {
      voyageNumber: updated.voyageNumber.value,
      previousMovementCount,
      updatedMovementCount: updated.schedule.carrierMovements.length,
    };
  }
}
