import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type {
  CustomsDeclarationRepository,
  CustomsHandlingContext,
} from '../../domain/repository/customs-declaration-repository.js';
import { CustomsDeclaration } from '../../domain/model/customs-declaration.js';
import { isCustomsStatus } from '../../domain/model/customs-status.js';
import { HandlingValidationError } from '../../domain/model/handling-validation-error.js';

/** 通関申告リポジトリの Kysely 実装 */
export class KyselyCustomsDeclarationRepository implements CustomsDeclarationRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(declaration: CustomsDeclaration): Promise<void> {
    await this.db
      .insertInto('customs_declaration')
      .values({
        handlingActivityId: declaration.handlingActivityId,
        declarationNumber: declaration.declarationNumber,
        declaredAt: declaration.declaredAt,
        status: declaration.status,
        clearedAt: declaration.clearedAt,
        remarks: declaration.remarks,
      })
      .execute();
  }

  async update(declaration: CustomsDeclaration): Promise<void> {
    await this.db
      .updateTable('customs_declaration')
      .set({
        status: declaration.status,
        clearedAt: declaration.clearedAt,
        remarks: declaration.remarks,
        updatedAt: new Date(),
      })
      .where('declarationNumber', '=', declaration.declarationNumber)
      .execute();
  }

  async findByDeclarationNumber(declarationNumber: string): Promise<CustomsDeclaration | null> {
    const row = await this.db
      .selectFrom('customs_declaration')
      .selectAll()
      .where('declarationNumber', '=', declarationNumber)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    if (!isCustomsStatus(row.status)) {
      throw new HandlingValidationError(`永続化された通関状態が不正です: ${row.status}`);
    }
    return CustomsDeclaration.reconstruct({
      declarationNumber: row.declarationNumber,
      handlingActivityId: row.handlingActivityId,
      status: row.status,
      declaredAt: new Date(row.declaredAt),
      clearedAt: row.clearedAt === null ? null : new Date(row.clearedAt),
      remarks: row.remarks,
    });
  }

  async findHandlingContext(handlingActivityId: number): Promise<CustomsHandlingContext | null> {
    const row = await this.db
      .selectFrom('handling_activity')
      .leftJoin('cargo', 'cargo.bookingId', 'handling_activity.bookingId')
      .select([
        'handling_activity.bookingId as bookingId',
        'handling_activity.locationUnlocode as location',
        'cargo.trackingNumber as trackingNumber',
      ])
      .where('handling_activity.id', '=', handlingActivityId)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    return { bookingId: row.bookingId, location: row.location, trackingNumber: row.trackingNumber };
  }
}
