import { Inject, Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { CARGO_REPOSITORY } from '../../booking.tokens.js';

interface HandlingRegisteredPayload {
  bookingId: string;
  misrouted: boolean;
}

/**
 * Booking Context の荷役登録イベントリスナー（US15・コミット後・冪等。ADR-005/009）。
 * LOAD/UNLOAD の場所不一致（misrouted）が確定したとき RoutingStatus を MISROUTED へ更新する。
 * 同値更新のため重複配信でも結果は変わらない（冪等）。失敗は発行側コマンドの失敗にしない。
 */
@Injectable()
export class BookingHandlingRegisteredListener {
  private readonly logger = new Logger(BookingHandlingRegisteredListener.name);

  constructor(@Inject(CARGO_REPOSITORY) private readonly cargos: CargoRepository) {}

  @OnEvent('handling.registered')
  async onHandlingActivityRegistered(payload: HandlingRegisteredPayload): Promise<void> {
    if (!payload.misrouted) {
      return;
    }
    try {
      await this.cargos.updateRoutingStatus(payload.bookingId, 'MISROUTED');
    } catch (error) {
      this.logger.error(`MISROUTED 反映に失敗（${payload.bookingId}）: ${String(error)}`);
    }
  }
}
