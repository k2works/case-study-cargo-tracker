import { Injectable } from '@nestjs/common';
import { EventEmitter2 } from '@nestjs/event-emitter';
import type { EventPublisher } from '../../application/commandservices/confirm-payment.service.js';

/**
 * EventPublisher の実装（NestJS EventEmitter2 アダプタ・Billing）。
 * 入金確認完了イベントを同一プロセス内へ配信する（ADR-005/009）。
 */
@Injectable()
export class BillingEventEmitterPublisher implements EventPublisher {
  constructor(private readonly emitter: EventEmitter2) {}

  emit(event: string, payload: unknown): void {
    this.emitter.emit(event, payload);
  }
}
