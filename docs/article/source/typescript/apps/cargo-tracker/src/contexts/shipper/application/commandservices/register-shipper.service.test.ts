import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Shipper } from '../../domain/model/shipper.js';
import { ShipperType } from '../../domain/model/value-objects.js';
import {
  EmailAlreadyRegisteredError,
  type ShipperRepository,
} from '../../domain/repository/shipper-repository.js';
import { RegisterShipperService } from './register-shipper.service.js';

describe('RegisterShipperService', () => {
  let repo: ShipperRepository;
  let service: RegisterShipperService;

  beforeEach(() => {
    repo = {
      save: vi.fn().mockResolvedValue(1),
      findByEmail: vi.fn().mockResolvedValue(null),
    };
    service = new RegisterShipperService(repo);
  });

  it('個人荷主を登録し ID と荷主コードを返す', async () => {
    const result = await service.register({
      shipperType: ShipperType.INDIVIDUAL,
      name: '山田太郎',
      email: 'yamada@example.com',
    });
    expect(result.id).toBe(1);
    expect(result.shipperCode).toMatch(/^SHP-/);
    expect(repo.save).toHaveBeenCalledOnce();
  });

  it('法人荷主を契約番号・割引率つきで登録する', async () => {
    await service.register({
      shipperType: ShipperType.CORPORATE,
      name: '株式会社サンプル',
      email: 'corp@example.com',
      contractNumber: 'CT-1',
      discountRate: 0.1,
    });
    const saved = vi.mocked(repo.save).mock.calls[0][0] as Shipper;
    expect(saved.shipperType).toBe(ShipperType.CORPORATE);
    expect(saved.discountRate.value).toBe(0.1);
  });

  it('Email が重複していれば EmailAlreadyRegisteredError を送出し保存しない', async () => {
    vi.mocked(repo.findByEmail).mockResolvedValue(
      Shipper.registerIndividual({ name: '既存', email: 'dup@example.com' }),
    );
    await expect(
      service.register({
        shipperType: ShipperType.INDIVIDUAL,
        name: '新規',
        email: 'dup@example.com',
      }),
    ).rejects.toBeInstanceOf(EmailAlreadyRegisteredError);
    expect(repo.save).not.toHaveBeenCalled();
  });

  it('法人で契約番号が欠落していれば登録できない', async () => {
    await expect(
      service.register({
        shipperType: ShipperType.CORPORATE,
        name: '株式会社サンプル',
        email: 'corp@example.com',
        discountRate: 0.1,
      }),
    ).rejects.toThrow();
    expect(repo.save).not.toHaveBeenCalled();
  });
});
