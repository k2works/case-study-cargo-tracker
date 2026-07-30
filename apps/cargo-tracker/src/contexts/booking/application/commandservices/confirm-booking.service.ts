import { Cargo } from '../../domain/model/cargo.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import {
  NotificationType,
  type NotificationPort,
} from '../outboundservices/acl/notification-port.js';
import type { ShipperContactAcl } from '../outboundservices/acl/shipper-contact-acl.js';
import { BookingNotFoundError } from './assign-to-routing.service.js';

/**
 * 予約確定・差戻し・キャンセルユースケース（US13）。
 * 営業担当者が経路提案（ROUTE_PROPOSED）の予約を確定・差戻し・キャンセルする。
 * キャンセル時は荷主（shipper）へキャンセル確認通知を送る（US12 是正・宛先は荷主メール）。
 */
export class ConfirmBookingService {
  constructor(
    private readonly cargos: CargoRepository,
    private readonly notifier: NotificationPort,
    private readonly shipperContacts: ShipperContactAcl,
  ) {}

  async confirm(bookingId: string): Promise<void> {
    const cargo = await this.load(bookingId);
    cargo.confirm();
    await this.cargos.update(cargo);
  }

  async returnToRouting(bookingId: string): Promise<void> {
    const cargo = await this.load(bookingId);
    cargo.returnToRouting();
    await this.cargos.update(cargo);
  }

  async cancel(bookingId: string): Promise<void> {
    const cargo = await this.load(bookingId);
    cargo.cancel();
    await this.cargos.update(cargo);
    const shipperEmail = await this.shipperContacts.findEmailByShipperId(cargo.shipperId);
    await this.notifier.notify({
      bookingId,
      notificationType: NotificationType.BOOKING_CANCELLED,
      recipient: shipperEmail ?? cargo.consignee.contactEmail,
    });
  }

  private async load(bookingId: string): Promise<Cargo> {
    const cargo = await this.cargos.findByBookingId(bookingId);
    if (cargo === null) {
      throw new BookingNotFoundError(bookingId);
    }
    return cargo;
  }
}
