import { randomUUID } from 'node:crypto';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import {
  NotificationType,
  type NotificationPort,
} from '../outboundservices/acl/notification-port.js';
import type { ShipperContactAcl } from '../outboundservices/acl/shipper-contact-acl.js';
import {
  TRACKING_NUMBER_ISSUED_EVENT,
  TrackingNumberIssuedEvent,
} from '../../domain/event/tracking-number-issued-event.js';
import { BookingNotFoundError } from './assign-to-routing.service.js';
import type { EventPublisher } from './book-cargo.service.js';

/**
 * 追跡番号発行ユースケース（US14）。
 * 経路設計者が確定済み（CONFIRMED）予約に一意の追跡番号を発行し、
 * TRACKING_ISSUED に遷移させ荷主へ通知する。
 * IT4 では採番を Booking 側で暫定的に行う（ADR-008・注 4。Tracking 集約実装時に採番主体を再配置）。
 */
export class AssignTrackingNumberService {
  constructor(
    private readonly cargos: CargoRepository,
    private readonly notifier: NotificationPort,
    private readonly shipperContacts: ShipperContactAcl,
    private readonly events: EventPublisher,
  ) {}

  async issue(bookingId: string): Promise<string> {
    const cargo = await this.cargos.findByBookingId(bookingId);
    if (cargo === null) {
      throw new BookingNotFoundError(bookingId);
    }
    const trackingNumber = AssignTrackingNumberService.nextTrackingNumber();
    cargo.issueTracking(trackingNumber);
    await this.cargos.update(cargo);
    const shipperEmail = await this.shipperContacts.findEmailByShipperId(cargo.shipperId);
    await this.notifier.notify({
      bookingId,
      notificationType: NotificationType.TRACKING_ISSUED,
      recipient: shipperEmail ?? cargo.consignee.contactEmail,
    });
    // コミット後発行（ADR-005/009）。Tracking 側が NOT_RECEIVED の追跡レコードを作成する
    this.events.emit(TRACKING_NUMBER_ISSUED_EVENT, new TrackingNumberIssuedEvent(bookingId, trackingNumber));
    return trackingNumber;
  }

  private static nextTrackingNumber(): string {
    return `TRK-${randomUUID().slice(0, 12).toUpperCase()}`;
  }
}
