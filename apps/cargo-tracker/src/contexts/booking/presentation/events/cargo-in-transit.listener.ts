import { Inject, Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { CARGO_REPOSITORY } from '../../booking.tokens.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import {
  HANDLING_ACTIVITY_REGISTERED_EVENT,
  type HandlingActivityRegisteredPayload,
} from '../../../../shared/contracts/handling-registered.contract.js';

/**
 * Booking Context の輸送開始リスナー（US21・コミット後・冪等。ADR-005/009）。
 * LOAD（積込）荷役イベントを購読し、貨物を TRACKING_ISSUED → IN_TRANSIT へ遷移させる。
 * 既に IN_TRANSIT 以降なら事前状態チェックでスキップする（重複配信でも結果は変わらない）。
 * 失敗は発行側コマンドの失敗にしない（ログのみ）。
 */
@Injectable()
export class CargoInTransitListener {
  private readonly logger = new Logger(CargoInTransitListener.name);

  constructor(@Inject(CARGO_REPOSITORY) private readonly cargos: CargoRepository) {}

  @OnEvent(HANDLING_ACTIVITY_REGISTERED_EVENT)
  async onHandlingActivityRegistered(payload: HandlingActivityRegisteredPayload): Promise<void> {
    if (payload.eventType !== 'LOAD') {
      return;
    }
    try {
      const cargo = await this.cargos.findByBookingId(payload.bookingId);
      // 対象状態でなければ何もしない（不正遷移エラーは握らず、事前チェックでスキップ）
      if (cargo === null || cargo.bookingStatus !== BookingStatus.TRACKING_ISSUED) {
        return;
      }
      cargo.markInTransit();
      await this.cargos.update(cargo);
    } catch (error) {
      this.logger.error(`IN_TRANSIT 反映に失敗（${payload.bookingId}）: ${String(error)}`);
    }
  }
}
