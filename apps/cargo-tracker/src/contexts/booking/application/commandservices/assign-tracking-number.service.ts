import { randomUUID } from 'node:crypto';
import { Cargo } from '../../domain/model/cargo.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import {
  NotificationType,
  type NotificationPort,
} from '../outboundservices/acl/notification-port.js';
import { BookingNotFoundError } from './assign-to-routing.service.js';

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
  ) {}

  async issue(bookingId: string): Promise<string> {
    const cargo = await this.cargos.findByBookingId(bookingId);
    if (cargo === null) {
      throw new BookingNotFoundError(bookingId);
    }
    const trackingNumber = AssignTrackingNumberService.nextTrackingNumber();
    cargo.issueTracking(trackingNumber);
    await this.cargos.update(cargo);
    await this.notifier.notify({
      bookingId,
      notificationType: NotificationType.TRACKING_ISSUED,
      recipient: cargo.consignee.contactEmail,
    });
    return trackingNumber;
  }

  private static nextTrackingNumber(): string {
    return `TRK-${randomUUID().slice(0, 12).toUpperCase()}`;
  }
}
