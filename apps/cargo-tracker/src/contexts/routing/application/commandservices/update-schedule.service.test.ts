import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { CarrierMovement, Schedule, Voyage } from '../../domain/model/voyage.js';
import type { VoyageRepository } from '../../domain/repository/voyage-repository.js';
import { UpdateScheduleService, VoyageNotFoundError } from './update-schedule.service.js';

function existingVoyage(): Voyage {
  return Voyage.register({
    voyageNumber: 'V001',
    shipName: 'Pacific Star',
    carrierName: 'Oceanic',
    supportedCargoTypes: [CargoType.GENERAL],
    schedule: Schedule.of([
      CarrierMovement.of({
        departureLocation: 'JPTYO',
        arrivalLocation: 'HKHKG',
        departureTime: new Date('2026-09-01T09:00:00Z'),
        arrivalTime: new Date('2026-09-04T10:00:00Z'),
      }),
    ]),
  });
}

describe('UpdateScheduleService', () => {
  let repo: VoyageRepository;
  let service: UpdateScheduleService;

  beforeEach(() => {
    repo = {
      save: vi.fn(),
      findAll: vi.fn(),
      findByVoyageNumber: vi.fn().mockResolvedValue(existingVoyage()),
      update: vi.fn().mockResolvedValue(undefined),
    };
    service = new UpdateScheduleService(repo);
  });

  it('既存航海のスケジュールを更新し差分を返す', async () => {
    const result = await service.update({
      voyageNumber: 'V001',
      carrierMovements: [
        {
          departureLocation: 'JPTYO',
          arrivalLocation: 'SGSIN',
          departureTime: new Date('2026-09-02T09:00:00Z'),
          arrivalTime: new Date('2026-09-09T10:00:00Z'),
        },
      ],
    });

    expect(repo.update).toHaveBeenCalledOnce();
    expect(result).toEqual({
      voyageNumber: 'V001',
      previousMovementCount: 1,
      updatedMovementCount: 1,
    });
  });

  it('未登録航海なら VoyageNotFoundError を送出し更新しない', async () => {
    vi.mocked(repo.findByVoyageNumber).mockResolvedValue(null);
    await expect(
      service.update({ voyageNumber: 'UNKNOWN', carrierMovements: [] }),
    ).rejects.toBeInstanceOf(VoyageNotFoundError);
    expect(repo.update).not.toHaveBeenCalled();
  });
});
