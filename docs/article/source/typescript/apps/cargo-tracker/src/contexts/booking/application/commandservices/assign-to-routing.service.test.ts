import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from '../../domain/model/cargo.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import type { EventPublisher } from './book-cargo.service.js';
import { AssignToRoutingService, BookingNotFoundError } from './assign-to-routing.service.js';

function makeCargo(): Cargo {
  return Cargo.book({
    shipperId: 1,
    cargoType: CargoType.GENERAL,
    weightKg: 1000,
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: new Date('2026-09-30'),
    consignee: { name: '受取', address: '', contactEmail: 'a@example.com' },
  });
}

describe('AssignToRoutingService', () => {
  let repo: CargoRepository;
  let publisher: EventPublisher;
  let service: AssignToRoutingService;

  beforeEach(() => {
    repo = { save: vi.fn(), findByBookingId: vi.fn(), update: vi.fn().mockResolvedValue(undefined), updateRoutingStatus: vi.fn() };
    publisher = { emit: vi.fn() };
    service = new AssignToRoutingService(repo, publisher);
  });

  it('仮受付の予約を ROUTING_IN_PROGRESS に更新し通知する', async () => {
    const cargo = makeCargo();
    vi.mocked(repo.findByBookingId).mockResolvedValue(cargo);
    await service.assign('bk-1');
    expect(cargo.bookingStatus).toBe(BookingStatus.ROUTING_IN_PROGRESS);
    expect(repo.update).toHaveBeenCalledWith(cargo);
    expect(publisher.emit).toHaveBeenCalled();
  });

  it('予約が存在しなければ BookingNotFoundError', async () => {
    vi.mocked(repo.findByBookingId).mockResolvedValue(null);
    await expect(service.assign('missing')).rejects.toBeInstanceOf(BookingNotFoundError);
  });
});
