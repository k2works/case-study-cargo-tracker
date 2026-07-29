import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from '../../domain/model/cargo.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { CARGO_ROUTED_EVENT } from '../../domain/event/cargo-routed-event.js';
import { BookingNotFoundError } from './assign-to-routing.service.js';
import { RouteCargoService } from './route-cargo.service.js';

function routableCargo(): Cargo {
  const cargo = Cargo.book({
    shipperId: 1,
    cargoType: CargoType.GENERAL,
    weightKg: 1000,
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: new Date('2026-09-30'),
    consignee: { name: '受取太郎', address: '大阪市', contactEmail: 'uke@example.com' },
  });
  cargo.assignToRouting();
  return cargo;
}

function command() {
  return {
    bookingId: 'bk-1',
    legs: [
      {
        voyageNumber: 'V001',
        loadLocation: 'JPTYO',
        unloadLocation: 'USLAX',
        loadTime: new Date('2026-09-01T00:00:00Z'),
        unloadTime: new Date('2026-09-20T00:00:00Z'),
      },
    ],
  };
}

describe('RouteCargoService（US09/US11）', () => {
  let cargos: CargoRepository;
  let events: { emit: ReturnType<typeof vi.fn> };
  let service: RouteCargoService;

  beforeEach(() => {
    cargos = {
      save: vi.fn(),
      findByBookingId: vi.fn(),
      update: vi.fn(),
    };
    events = { emit: vi.fn() };
    service = new RouteCargoService(cargos, events);
  });

  it('選択した経路を予約に紐付け ROUTE_PROPOSED に遷移し CargoRoutedEvent を発行する', async () => {
    const cargo = routableCargo();
    vi.mocked(cargos.findByBookingId).mockResolvedValue(cargo);

    await service.route(command());

    expect(cargo.bookingStatus).toBe(BookingStatus.ROUTE_PROPOSED);
    expect(cargo.cargoItinerary?.legs[0].voyageNumber).toBe('V001');
    expect(cargos.update).toHaveBeenCalledWith(cargo);
    expect(events.emit).toHaveBeenCalledWith(CARGO_ROUTED_EVENT, expect.objectContaining({ bookingId: 'bk-1' }));
  });

  it('予約が見つからない場合は BookingNotFoundError', async () => {
    vi.mocked(cargos.findByBookingId).mockResolvedValue(null);
    await expect(service.route(command())).rejects.toThrow(BookingNotFoundError);
  });
});
