import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from '../../domain/model/cargo.js';
import { CargoItinerary, Leg } from '../../domain/model/cargo-itinerary.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { NotificationType } from '../outboundservices/acl/notification-port.js';
import { BookingNotFoundError } from './assign-to-routing.service.js';
import { AssignTrackingNumberService } from './assign-tracking-number.service.js';

function confirmedCargo(): Cargo {
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
  cargo.confirm();
  return cargo;
}

describe('AssignTrackingNumberService（US14）', () => {
  let cargos: CargoRepository;
  let notifier: { notify: ReturnType<typeof vi.fn> };
  let shipperContacts: { findEmailByShipperId: ReturnType<typeof vi.fn> };
  let events: { emit: ReturnType<typeof vi.fn> };
  let service: AssignTrackingNumberService;

  beforeEach(() => {
    cargos = { save: vi.fn(), findByBookingId: vi.fn(), update: vi.fn(), updateRoutingStatus: vi.fn() };
    notifier = { notify: vi.fn() };
    shipperContacts = { findEmailByShipperId: vi.fn().mockResolvedValue('shipper@example.com') };
    events = { emit: vi.fn() };
    service = new AssignTrackingNumberService(cargos, notifier, shipperContacts, events);
  });

  it('確定済み予約に一意の追跡番号を発行し TRACKING_ISSUED に遷移、荷主（shipper）へ通知する', async () => {
    const cargo = confirmedCargo();
    vi.mocked(cargos.findByBookingId).mockResolvedValue(cargo);

    const trackingNumber = await service.issue('bk-1');

    expect(trackingNumber).toMatch(/^TRK-/);
    expect(cargo.bookingStatus).toBe(BookingStatus.TRACKING_ISSUED);
    expect(cargo.trackingNumber).toBe(trackingNumber);
    expect(cargos.update).toHaveBeenCalledWith(cargo);
    expect(shipperContacts.findEmailByShipperId).toHaveBeenCalledWith(cargo.shipperId);
    expect(notifier.notify).toHaveBeenCalledWith({
      bookingId: 'bk-1',
      notificationType: NotificationType.TRACKING_ISSUED,
      recipient: 'shipper@example.com',
    });
    // コミット後に追跡番号発行イベントを発行する（Tracking 側で NOT_RECEIVED の追跡レコードを作成）
    expect(events.emit).toHaveBeenCalledWith(
      'booking.tracking-issued',
      expect.objectContaining({ bookingId: 'bk-1', trackingNumber }),
    );
  });

  it('連続発行で異なる追跡番号を採番する', async () => {
    vi.mocked(cargos.findByBookingId).mockImplementation(async () => confirmedCargo());
    const first = await service.issue('bk-1');
    const second = await service.issue('bk-2');
    expect(first).not.toBe(second);
  });

  it('予約が見つからない場合は BookingNotFoundError', async () => {
    vi.mocked(cargos.findByBookingId).mockResolvedValue(null);
    await expect(service.issue('missing')).rejects.toThrow(BookingNotFoundError);
  });
});
