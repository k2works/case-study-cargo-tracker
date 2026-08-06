import { Injectable } from '@nestjs/common';
import { EventEmitter2 } from '@nestjs/event-emitter';
import type { EventPublisher } from '../../application/commandservices/register-handling-activity.service.js';

/**
 * EventPublisher の実装（NestJS EventEmitter2 アダプタ・Handling Context）。
 * ドメインイベントはトランザクションコミット後に emit する（ADR-005/009）。
 */
@Injectable()
export class HandlingEventEmitterPublisher implements EventPublisher {
  constructor(private readonly emitter: EventEmitter2) {}

  emit(event: string, payload: unknown): void {
    this.emitter.emit(event, payload);
  }
}
