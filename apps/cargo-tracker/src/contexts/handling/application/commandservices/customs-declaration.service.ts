import { randomUUID } from 'node:crypto';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { HandlingValidationError } from '../../domain/model/handling-validation-error.js';

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

const CUSTOMS_STATUSES = ['PENDING', 'CLEARED', 'HELD', 'REJECTED'] as const;

export interface RegisterCustomsDeclarationCommand {
  handlingActivityId: number;
  declaredAt: Date;
  remarks?: string | null;
}

/**
 * 通関申告ユースケース（US16 前提条件）。
 * - register: 通関申告を PENDING で新規登録（RegisterCustomsDeclarationCommand・荷役作業員）
 * - updateStatus: 税関システム（CustomsClearancePort スタブ ACL）からの状態更新（UpdateCustomsStatusCommand）
 * 通関ステータス画面（/tracking/{tn}/customs）は IT6 スコープのため、本 IT はコマンドのみ提供する（計画 注 11）。
 */
export class CustomsDeclarationService {
  constructor(private readonly db: AppDatabase) {}

  async register(command: RegisterCustomsDeclarationCommand): Promise<string> {
    const activity = await this.db
      .selectFrom('handling_activity')
      .select('id')
      .where('id', '=', command.handlingActivityId)
      .executeTakeFirst();
    if (activity === undefined) {
      throw new HandlingActivityNotFoundError(command.handlingActivityId);
    }
    const declarationNumber = `DECL-${randomUUID().slice(0, 12).toUpperCase()}`;
    await this.db
      .insertInto('customs_declaration')
      .values({
        handlingActivityId: command.handlingActivityId,
        declarationNumber,
        declaredAt: command.declaredAt,
        status: 'PENDING',
        remarks: command.remarks ?? null,
      })
      .execute();
    return declarationNumber;
  }

  async updateStatus(declarationNumber: string, status: string): Promise<void> {
    if (!CUSTOMS_STATUSES.includes(status as (typeof CUSTOMS_STATUSES)[number])) {
      throw new HandlingValidationError(`不正な通関状態: ${status}`);
    }
    const result = await this.db
      .updateTable('customs_declaration')
      .set({
        status,
        clearedAt: status === 'CLEARED' ? new Date() : null,
        updatedAt: new Date(),
      })
      .where('declarationNumber', '=', declarationNumber)
      .executeTakeFirst();
    if (result.numUpdatedRows === 0n) {
      throw new DeclarationNotFoundError(declarationNumber);
    }
  }
}
