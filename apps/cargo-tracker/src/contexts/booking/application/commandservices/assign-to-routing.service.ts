import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import type { EventPublisher } from './book-cargo.service.js';

/** 経路設計依頼イベント名 */
export const ROUTING_REQUESTED_EVENT = 'booking.routing_requested';

/** 予約が見つからない場合のエラー */
export class BookingNotFoundError extends Error {
  constructor(readonly bookingId: string) {
    super(`予約が見つかりません: ${bookingId}`);
    this.name = 'BookingNotFoundError';
  }
}

/**
 * 経路設計者への引き渡しユースケース（US06）。
 * 予約を ROUTING_IN_PROGRESS に遷移させ、経路設計者へ通知する。
 */
export class AssignToRoutingService {
  constructor(
    private readonly cargos: CargoRepository,
    private readonly events: EventPublisher,
  ) {}

  async assign(bookingId: string): Promise<void> {
    const cargo = await this.cargos.findByBookingId(bookingId);
    if (cargo === null) {
      throw new BookingNotFoundError(bookingId);
    }
    cargo.assignToRouting();
    await this.cargos.update(cargo);
    this.events.emit(ROUTING_REQUESTED_EVENT, { bookingId });
  }
}
