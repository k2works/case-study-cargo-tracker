import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import {
  ShipperNotFoundError,
  type ShipperExistenceChecker,
} from '../outboundservices/acl/shipper-existence-checker.js';
import { CARGO_BOOKED_EVENT } from '../../domain/event/cargo-booked-event.js';
import { BookCargoService, type EventPublisher } from './book-cargo.service.js';

function command(overrides = {}) {
  return {
    shipperCode: 'SHP-abc12345',
    cargoType: CargoType.GENERAL,
    weightKg: 1000,
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: new Date('2026-09-30'),
    consignee: { name: '受取太郎', address: '大阪市', contactEmail: 'uke@example.com' },
    ...overrides,
  };
}

describe('BookCargoService', () => {
  let repo: CargoRepository;
  let checker: ShipperExistenceChecker;
  let publisher: EventPublisher;
  let service: BookCargoService;

  beforeEach(() => {
    repo = { save: vi.fn().mockResolvedValue(1), findByBookingId: vi.fn(), update: vi.fn(), updateRoutingStatus: vi.fn() };
    checker = { resolveShipperId: vi.fn().mockResolvedValue(1) };
    publisher = { emit: vi.fn() };
    service = new BookCargoService(repo, checker, publisher);
  });

  it('荷主が存在すれば予約を保存し BookingId を返す', async () => {
    const result = await service.book(command());
    expect(result.bookingId).toMatch(/^[0-9a-f-]{36}$/);
    expect(repo.save).toHaveBeenCalledOnce();
  });

  it('CargoBookedEvent を正しいペイロードで発行する（BC 間契約）', async () => {
    await service.book(command());
    expect(publisher.emit).toHaveBeenCalledWith(
      CARGO_BOOKED_EVENT,
      expect.objectContaining({
        shipperId: 1,
        origin: 'JPTYO',
        destination: 'USLAX',
        bookingId: expect.stringMatching(/^[0-9a-f-]{36}$/),
      }),
    );
  });

  it('荷主が存在しなければ ShipperNotFoundError を送出し保存しない', async () => {
    vi.mocked(checker.resolveShipperId).mockResolvedValue(null);
    await expect(service.book(command())).rejects.toBeInstanceOf(ShipperNotFoundError);
    expect(repo.save).not.toHaveBeenCalled();
    expect(publisher.emit).not.toHaveBeenCalled();
  });

  it('危険物で申告欠落なら検証エラー（保存しない）', async () => {
    await expect(
      service.book(command({ cargoType: CargoType.HAZARDOUS })),
    ).rejects.toThrow();
    expect(repo.save).not.toHaveBeenCalled();
  });
});
