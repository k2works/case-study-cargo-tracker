import { Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import { TrackCargoService } from '../../application/commandservices/track-cargo.service.js';
import type { TrackingNumberIssuedPayload } from '../../../../shared/contracts/tracking-number-issued.contract.js';
import type { HandlingActivityRegisteredPayload } from '../../../../shared/contracts/handling-registered.contract.js';

/**
 * Tracking Context のイベントリスナー（コミット後・冪等。ADR-005/009）。
 * - booking.tracking-issued: NOT_RECEIVED の追跡レコードを作成（Try T6）
 * - handling.registered: 荷役種別に応じて貨物状態を自動更新（US15）
 * リスナーの失敗は発行側コマンドの失敗にしない（例外を握りログに記録する）。
 */
@Injectable()
export class TrackingEventListener {
  private readonly logger = new Logger(TrackingEventListener.name);

  constructor(private readonly trackCargo: TrackCargoService) {}

  @OnEvent('booking.tracking-issued')
  async onTrackingNumberIssued(payload: TrackingNumberIssuedPayload): Promise<void> {
    try {
      await this.trackCargo.createIfAbsent(payload.trackingNumber, payload.bookingId);
    } catch (error) {
      this.logger.error(`追跡レコード作成に失敗（${payload.trackingNumber}）: ${String(error)}`);
    }
  }

  @OnEvent('handling.registered')
  async onHandlingActivityRegistered(payload: HandlingActivityRegisteredPayload): Promise<void> {
    try {
      await this.trackCargo.applyHandlingEvent({
        trackingNumber: payload.trackingNumber,
        bookingId: payload.bookingId,
        event: {
          eventType: payload.eventType,
          location: payload.location,
          completionTime: new Date(payload.completionTime),
          voyageNumber: payload.voyageNumber,
        },
      });
    } catch (error) {
      this.logger.error(`荷役イベントの追跡反映に失敗（${payload.trackingNumber}）: ${String(error)}`);
    }
  }
}
