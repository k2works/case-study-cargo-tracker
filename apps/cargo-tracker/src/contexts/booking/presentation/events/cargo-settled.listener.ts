import { Inject, Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { CARGO_REPOSITORY } from '../../booking.tokens.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import {
  PAYMENT_CONFIRMED_EVENT,
  type PaymentConfirmedPayload,
} from '../../../../shared/contracts/payment-confirmed.contract.js';

/**
 * Booking Context の精算完了リスナー（US23-4・コミット後・冪等。ADR-005/009）。
 * 入金確認完了イベント（PAYMENT_CONFIRMED_EVENT）を購読し、貨物を DELIVERED → SETTLED へ遷移させる。
 * 既に SETTLED（または DELIVERED 以外）なら事前状態チェックでスキップする（重複配信でも結果は変わらない）。
 * 失敗は発行側の入金確認を失敗にしない（ログのみ）。
 */
@Injectable()
export class CargoSettledListener {
  private readonly logger = new Logger(CargoSettledListener.name);

  constructor(@Inject(CARGO_REPOSITORY) private readonly cargos: CargoRepository) {}

  @OnEvent(PAYMENT_CONFIRMED_EVENT)
  async onPaymentConfirmed(payload: PaymentConfirmedPayload): Promise<void> {
    try {
      const cargo = await this.cargos.findByBookingId(payload.bookingId);
      if (cargo === null || cargo.bookingStatus !== BookingStatus.DELIVERED) {
        return;
      }
      cargo.settle();
      await this.cargos.update(cargo);
    } catch (error) {
      this.logger.error(`SETTLED 反映に失敗（${payload.bookingId}）: ${String(error)}`);
    }
  }
}
