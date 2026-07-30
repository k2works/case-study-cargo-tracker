import { randomUUID } from 'node:crypto';
import { Logger } from '@nestjs/common';
import { CustomsDeclaration } from '../../domain/model/customs-declaration.js';
import { CustomsStatus, isCustomsStatus } from '../../domain/model/customs-status.js';
import { HandlingValidationError } from '../../domain/model/handling-validation-error.js';
import { CUSTOMS_HELD_EVENT, CustomsHeldEvent } from '../../domain/event/customs-held-event.js';
import type { CustomsDeclarationRepository } from '../../domain/repository/customs-declaration-repository.js';
import type { EventPublisher } from './register-handling-activity.service.js';

export class HandlingActivityNotFoundError extends Error {
  constructor(id: number) {
    super(`荷役作業が見つかりません: ${id}`);
    this.name = 'HandlingActivityNotFoundError';
  }
}

export class DeclarationNotFoundError extends Error {
  constructor(declarationNumber: string) {
    super(`通関申告が見つかりません: ${declarationNumber}`);
    this.name = 'DeclarationNotFoundError';
  }
}

export interface RegisterCustomsDeclarationCommand {
  handlingActivityId: number;
  declaredAt: Date;
  remarks?: string | null;
}

/**
 * 通関申告ユースケース（US16 前提条件・ADR-010）。
 * - register: 通関申告を PENDING で新規登録（RegisterCustomsDeclarationCommand・荷役作業員）
 * - updateStatus: 状態更新を集約の遷移メソッド（clear/hold/reject）へ委譲する
 * HELD 遷移成功後は CustomsHeldEvent をコミット後 emit し、Tracking が CUSTOMS_HOLD 例外を登録する。
 */
export class CustomsDeclarationService {
  private readonly logger = new Logger(CustomsDeclarationService.name);

  constructor(
    private readonly declarations: CustomsDeclarationRepository,
    private readonly events: EventPublisher,
  ) {}

  async register(command: RegisterCustomsDeclarationCommand): Promise<string> {
    const context = await this.declarations.findHandlingContext(command.handlingActivityId);
    if (context === null) {
      throw new HandlingActivityNotFoundError(command.handlingActivityId);
    }
    const declarationNumber = `DECL-${randomUUID().slice(0, 12).toUpperCase()}`;
    const declaration = CustomsDeclaration.register({
      declarationNumber,
      handlingActivityId: command.handlingActivityId,
      declaredAt: command.declaredAt,
      remarks: command.remarks,
    });
    await this.declarations.save(declaration);
    return declarationNumber;
  }

  async updateStatus(declarationNumber: string, status: string): Promise<void> {
    if (!isCustomsStatus(status)) {
      throw new HandlingValidationError(`不正な通関状態: ${status}`);
    }
    const declaration = await this.declarations.findByDeclarationNumber(declarationNumber);
    if (declaration === null) {
      throw new DeclarationNotFoundError(declarationNumber);
    }
    this.applyTransition(declaration, status);
    await this.declarations.update(declaration);

    if (status === CustomsStatus.HELD) {
      await this.emitHeld(declaration.handlingActivityId, declarationNumber);
    }
  }

  private applyTransition(declaration: CustomsDeclaration, status: CustomsStatus): void {
    switch (status) {
      case CustomsStatus.CLEARED:
        declaration.clear();
        return;
      case CustomsStatus.HELD:
        declaration.hold();
        return;
      case CustomsStatus.REJECTED:
        declaration.reject();
        return;
      case CustomsStatus.PENDING:
        throw new HandlingValidationError('通関申告を PENDING へ戻すことはできません');
    }
  }

  /** コミット後副作用（ADR-009）。発行失敗はコマンド失敗にしない（例外を握りログに記録する） */
  private async emitHeld(handlingActivityId: number, declarationNumber: string): Promise<void> {
    try {
      const context = await this.declarations.findHandlingContext(handlingActivityId);
      if (context === null) {
        return;
      }
      this.events.emit(
        CUSTOMS_HELD_EVENT,
        new CustomsHeldEvent(context.bookingId, context.trackingNumber, declarationNumber, context.location),
      );
    } catch (error) {
      this.logger.error(`通関留置イベントの発行に失敗（例外自動登録が保留）: ${String(error)}`);
    }
  }
}
