import { Inject, Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { CARGO_REPOSITORY } from '../../booking.tokens.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import {
  CARGO_CLAIMED_EVENT,
  type CargoClaimedPayload,
} from '../../../../shared/contracts/cargo-claimed.contract.js';

/**
 * Booking Context の配達完了リスナー（US21・コミット後・冪等。ADR-005/009）。
 * 引取完了イベント（CargoClaimedEvent）を購読し、貨物を IN_TRANSIT → DELIVERED へ遷移させる。
 * 既に DELIVERED 以降なら事前状態チェックでスキップする（重複配信でも結果は変わらない）。
 * 失敗は発行側コマンドの失敗にしない（ログのみ）。
 */
@Injectable()
export class CargoDeliveredListener {
  private readonly logger = new Logger(CargoDeliveredListener.name);

  constructor(@Inject(CARGO_REPOSITORY) private readonly cargos: CargoRepository) {}

  @OnEvent(CARGO_CLAIMED_EVENT)
  async onCargoClaimed(payload: CargoClaimedPayload): Promise<void> {
    try {
      const cargo = await this.cargos.findByBookingId(payload.bookingId);
      // 対象状態でなければ何もしない（不正遷移エラーは握らず、事前チェックでスキップ）
      if (cargo === null || cargo.bookingStatus !== BookingStatus.IN_TRANSIT) {
        return;
      }
      cargo.markDelivered();
      await this.cargos.update(cargo);
    } catch (error) {
      this.logger.error(`DELIVERED 反映に失敗（${payload.bookingId}）: ${String(error)}`);
    }
  }
}
