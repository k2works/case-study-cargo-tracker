import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from '../../domain/model/cargo.js';
import { CargoItinerary, Leg } from '../../domain/model/cargo-itinerary.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { NotificationType } from '../outboundservices/acl/notification-port.js';
import { BookingNotFoundError } from './assign-to-routing.service.js';
import { ConfirmBookingService } from './confirm-booking.service.js';

function proposedCargo(): Cargo {
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
  cargo.assignRoute(
    CargoItinerary.of([
      Leg.of({
        voyageNumber: 'V001',
        loadLocation: 'JPTYO',
        unloadLocation: 'USLAX',
        loadTime: new Date('2026-09-01T00:00:00Z'),
        unloadTime: new Date('2026-09-20T00:00:00Z'),
      }),
    ]),
  );
  return cargo;
}

describe('ConfirmBookingService（US13）', () => {
  let cargos: CargoRepository;
  let notifier: { notify: ReturnType<typeof vi.fn> };
  let shipperContacts: { findEmailByShipperId: ReturnType<typeof vi.fn> };
  let service: ConfirmBookingService;

  beforeEach(() => {
    cargos = { save: vi.fn(), findByBookingId: vi.fn(), update: vi.fn() };
    notifier = { notify: vi.fn() };
    shipperContacts = { findEmailByShipperId: vi.fn().mockResolvedValue('shipper@example.com') };
    service = new ConfirmBookingService(cargos, notifier, shipperContacts);
  });

  it('予約を確定すると CONFIRMED になる', async () => {
    const cargo = proposedCargo();
    vi.mocked(cargos.findByBookingId).mockResolvedValue(cargo);
    await service.confirm('bk-1');
    expect(cargo.bookingStatus).toBe(BookingStatus.CONFIRMED);
    expect(cargos.update).toHaveBeenCalledWith(cargo);
  });

  it('差戻しで ROUTING_IN_PROGRESS に戻る', async () => {
    const cargo = proposedCargo();
    vi.mocked(cargos.findByBookingId).mockResolvedValue(cargo);
    await service.returnToRouting('bk-1');
    expect(cargo.bookingStatus).toBe(BookingStatus.ROUTING_IN_PROGRESS);
  });

  it('キャンセルすると CANCELLED になり荷主（shipper）へキャンセル通知を送る', async () => {
    const cargo = proposedCargo();
    vi.mocked(cargos.findByBookingId).mockResolvedValue(cargo);
    await service.cancel('bk-1');
    expect(cargo.bookingStatus).toBe(BookingStatus.CANCELLED);
    expect(shipperContacts.findEmailByShipperId).toHaveBeenCalledWith(cargo.shipperId);
    expect(notifier.notify).toHaveBeenCalledWith({
      bookingId: 'bk-1',
      notificationType: NotificationType.BOOKING_CANCELLED,
      recipient: 'shipper@example.com',
    });
  });

  it('荷主メールが解決できない場合は荷受人メールへフォールバックして通知する', async () => {
    const cargo = proposedCargo();
    vi.mocked(cargos.findByBookingId).mockResolvedValue(cargo);
    shipperContacts.findEmailByShipperId.mockResolvedValue(null);
    await service.cancel('bk-1');
    expect(notifier.notify).toHaveBeenCalledWith(
      expect.objectContaining({ recipient: 'uke@example.com' }),
    );
  });

  it('予約が見つからない場合は BookingNotFoundError', async () => {
    vi.mocked(cargos.findByBookingId).mockResolvedValue(null);
    await expect(service.confirm('missing')).rejects.toThrow(BookingNotFoundError);
  });
});
