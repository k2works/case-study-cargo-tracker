import { Injectable } from '@nestjs/common';
import { EventEmitter2 } from '@nestjs/event-emitter';
import type { EventPublisher } from '../../application/commandservices/book-cargo.service.js';

/**
 * EventPublisher の実装（NestJS EventEmitter2 アダプタ）。
 * ドメインイベントを同一プロセス内へ配信する（ADR-005）。
 */
@Injectable()
export class EventEmitterPublisher implements EventPublisher {
  constructor(private readonly emitter: EventEmitter2) {}

  emit(event: string, payload: unknown): void {
    this.emitter.emit(event, payload);
  }
}
