import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import type { VoyageRepository } from '../../domain/repository/voyage-repository.js';
import { RegisterVoyageService } from './register-voyage.service.js';

function command(overrides = {}) {
  return {
    voyageNumber: 'V001',
    shipName: 'Pacific Star',
    carrierName: 'Oceanic',
    supportedCargoTypes: [CargoType.GENERAL, CargoType.REFRIGERATED],
    carrierMovements: [
      {
        departureLocation: 'JPTYO',
        arrivalLocation: 'SGSIN',
        departureTime: new Date('2026-09-01T09:00:00Z'),
        arrivalTime: new Date('2026-09-08T08:00:00Z'),
      },
    ],
    ...overrides,
  };
}

describe('RegisterVoyageService', () => {
  let repo: VoyageRepository;
  let service: RegisterVoyageService;

  beforeEach(() => {
    repo = { save: vi.fn().mockResolvedValue(1), findAll: vi.fn(), findByVoyageNumber: vi.fn(), update: vi.fn() };
    service = new RegisterVoyageService(repo);
  });

  it('航海を登録し voyageNumber と登録 ID を返す', async () => {
    const result = await service.register(command());
    expect(result).toEqual({ id: 1, voyageNumber: 'V001' });
    expect(repo.save).toHaveBeenCalledWith(
      expect.objectContaining({
        shipName: 'Pacific Star',
        carrierName: 'Oceanic',
      }),
    );
  });

  it('日付整合性エラー時は保存しない', async () => {
    await expect(
      service.register(
        command({
          carrierMovements: [
            {
              departureLocation: 'JPTYO',
              arrivalLocation: 'SGSIN',
              departureTime: new Date('2026-09-08T09:00:00Z'),
              arrivalTime: new Date('2026-09-01T08:00:00Z'),
            },
          ],
        }),
      ),
    ).rejects.toThrow('出発時刻は到着時刻以前である必要があります');
    expect(repo.save).not.toHaveBeenCalled();
  });
});
