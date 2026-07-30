import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { HandlingActivityRepository } from '../../domain/repository/handling-activity-repository.js';
import { HandlingActivity } from '../../domain/model/handling-activity.js';

/** 荷役作業リポジトリの Kysely 実装 */
export class KyselyHandlingActivityRepository implements HandlingActivityRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(activity: HandlingActivity): Promise<HandlingActivity> {
    const row = await this.db
      .insertInto('handling_activity')
      .values({
        bookingId: activity.bookingId,
        eventType: activity.type.value,
        eventCompletionTime: activity.completionTime,
        locationUnlocode: activity.location.unlocode,
        voyageNumber: activity.voyageNumber?.value ?? null,
        operatorName: activity.operatorName,
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    return HandlingActivity.reconstruct({
      id: row.id,
      bookingId: activity.bookingId,
      type: activity.type.value,
      location: activity.location.unlocode,
      completionTime: activity.completionTime,
      voyageNumber: activity.voyageNumber?.value ?? null,
      operatorName: activity.operatorName,
    });
  }

  async findByBookingId(bookingId: string): Promise<HandlingActivity[]> {
    const rows = await this.db
      .selectFrom('handling_activity')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .orderBy('eventCompletionTime', 'asc')
      .execute();
    return rows.map((row) =>
      HandlingActivity.reconstruct({
        id: row.id,
        bookingId: row.bookingId,
        type: row.eventType,
        location: row.locationUnlocode,
        completionTime: new Date(row.eventCompletionTime),
        voyageNumber: row.voyageNumber,
        operatorName: row.operatorName,
      }),
    );
  }
}
