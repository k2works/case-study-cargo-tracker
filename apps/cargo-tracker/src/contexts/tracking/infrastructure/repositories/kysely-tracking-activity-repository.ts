import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { TrackingActivityRepository } from '../../domain/repository/tracking-activity-repository.js';
import { TrackingActivity, type TrackingEvent } from '../../domain/model/tracking-activity.js';

/**
 * 追跡レコードリポジトリの Kysely 実装。
 * save は新規作成/イベント差分追加の upsert として動作し、transport_status を集約の導出状態と同期する。
 */
export class KyselyTrackingActivityRepository implements TrackingActivityRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(activity: TrackingActivity): Promise<TrackingActivity> {
    return this.db.transaction().execute(async (trx) => {
      let id = activity.id;
      if (id === null) {
        const row = await trx
          .insertInto('tracking_activity')
          .values({
            trackingNumber: activity.trackingNumber,
            bookingId: activity.bookingId,
            transportStatus: activity.currentStatus(),
          })
          .returning('id')
          .executeTakeFirstOrThrow();
        id = row.id;
      } else {
        await trx
          .updateTable('tracking_activity')
          .set({ transportStatus: activity.currentStatus(), updatedAt: new Date() })
          .where('id', '=', id)
          .execute();
      }
      const existing = await trx
        .selectFrom('tracking_handling_event')
        .select(['eventType', 'eventTime'])
        .where('trackingId', '=', id)
        .execute();
      const persisted = new Set(existing.map((e) => `${e.eventType}@${new Date(e.eventTime).getTime()}`));
      for (const event of activity.events) {
        if (persisted.has(`${event.eventType}@${event.completionTime.getTime()}`)) {
          continue;
        }
        await trx
          .insertInto('tracking_handling_event')
          .values({
            trackingId: id,
            eventType: event.eventType,
            eventTime: event.completionTime,
            locationUnlocode: event.location,
            voyageNumber: event.voyageNumber,
          })
          .execute();
      }
      return TrackingActivity.reconstruct({
        id,
        trackingNumber: activity.trackingNumber,
        bookingId: activity.bookingId,
        events: [...activity.events],
      });
    });
  }

  async findByTrackingNumber(trackingNumber: string): Promise<TrackingActivity | null> {
    const row = await this.db
      .selectFrom('tracking_activity')
      .select(['id', 'trackingNumber', 'bookingId'])
      .where('trackingNumber', '=', trackingNumber)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    const eventRows = await this.db
      .selectFrom('tracking_handling_event')
      .selectAll()
      .where('trackingId', '=', row.id)
      .orderBy('eventTime', 'asc')
      .execute();
    const events: TrackingEvent[] = eventRows.map((e) => ({
      eventType: e.eventType,
      location: e.locationUnlocode ?? '',
      completionTime: new Date(e.eventTime),
      voyageNumber: e.voyageNumber,
    }));
    return TrackingActivity.reconstruct({
      id: row.id,
      trackingNumber: row.trackingNumber,
      bookingId: row.bookingId,
      events,
    });
  }
}
