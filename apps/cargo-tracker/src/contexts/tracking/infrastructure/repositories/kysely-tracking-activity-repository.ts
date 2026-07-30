import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { TrackingActivityRepository } from '../../domain/repository/tracking-activity-repository.js';
import { TrackingActivity, type TrackingEvent } from '../../domain/model/tracking-activity.js';
import { TrackingExceptionEvent } from '../../domain/model/tracking-exception.js';
import type { ExceptionType } from '../../domain/model/exception-type.js';
import type { TrackingStatus } from '../../domain/model/tracking-status.js';

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
        // 遅延作成の競合冪等化（Try T6）: 別プロセスが同一 tracking_number を先に作成していても
        // UNIQUE 衝突で失敗させず、既存行を再読込して以降のイベント差分追記までやり切る。
        const inserted = await trx
          .insertInto('tracking_activity')
          .values({
            trackingNumber: activity.trackingNumber,
            bookingId: activity.bookingId,
            transportStatus: activity.currentStatus(),
          })
          .onConflict((oc) => oc.column('trackingNumber').doNothing())
          .returning('id')
          .executeTakeFirst();
        if (inserted === undefined) {
          // 衝突 = 別プロセスが作成済み。既存行の id を取得して差分追記を継続する。
          const existing = await trx
            .selectFrom('tracking_activity')
            .select('id')
            .where('trackingNumber', '=', activity.trackingNumber)
            .executeTakeFirstOrThrow();
          id = existing.id;
        } else {
          id = inserted.id;
        }
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
      // 例外の差分永続化: id 未採番は挿入し採番 ID を集約へ反映、既存は対応報告・解決の更新を書き戻す。
      for (const exception of activity.exceptions) {
        if (exception.id === null) {
          const inserted = await trx
            .insertInto('tracking_exception_event')
            .values({
              trackingId: id,
              exceptionType: exception.exceptionType,
              occurredAt: exception.occurredAt,
              escalationFlag: exception.escalationFlag,
              locationUnlocode: exception.location,
              description: exception.description,
              statusBeforeException: exception.statusBeforeException,
              declarationNumber: exception.declarationNumber,
              resolvedAt: exception.resolvedAt,
              resolutionNotes: exception.resolutionNotes,
              reportedAt: exception.reportedAt,
              newEstimatedArrival: exception.newEstimatedArrival,
              reportNotes: exception.reportNotes,
            })
            .returning('id')
            .executeTakeFirstOrThrow();
          exception.assignId(inserted.id);
        } else {
          await trx
            .updateTable('tracking_exception_event')
            .set({
              resolvedAt: exception.resolvedAt,
              resolutionNotes: exception.resolutionNotes,
              reportedAt: exception.reportedAt,
              newEstimatedArrival: exception.newEstimatedArrival,
              reportNotes: exception.reportNotes,
              updatedAt: new Date(),
            })
            .where('id', '=', exception.id)
            .execute();
        }
      }
      return TrackingActivity.reconstruct({
        id,
        trackingNumber: activity.trackingNumber,
        bookingId: activity.bookingId,
        events: [...activity.events],
        exceptions: [...activity.exceptions],
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
    // イベントと例外は独立して読めるため並列取得し、追跡反映（US15 リスナー）の遅延を抑える。
    const [eventRows, exceptionRows] = await Promise.all([
      this.db
        .selectFrom('tracking_handling_event')
        .selectAll()
        .where('trackingId', '=', row.id)
        .orderBy('eventTime', 'asc')
        .execute(),
      this.db
        .selectFrom('tracking_exception_event')
        .selectAll()
        .where('trackingId', '=', row.id)
        .orderBy('occurredAt', 'asc')
        .execute(),
    ]);
    const events: TrackingEvent[] = eventRows.map((e) => ({
      eventType: e.eventType,
      location: e.locationUnlocode ?? '',
      completionTime: new Date(e.eventTime),
      voyageNumber: e.voyageNumber,
    }));
    const exceptions = exceptionRows.map((e) =>
      TrackingExceptionEvent.reconstruct({
        id: e.id,
        exceptionType: e.exceptionType as ExceptionType,
        location: e.locationUnlocode ?? '',
        occurredAt: new Date(e.occurredAt),
        description: e.description,
        escalationFlag: e.escalationFlag,
        statusBeforeException: e.statusBeforeException as TrackingStatus,
        declarationNumber: e.declarationNumber,
        resolvedAt: e.resolvedAt === null ? null : new Date(e.resolvedAt),
        resolutionNotes: e.resolutionNotes,
        reportedAt: e.reportedAt === null ? null : new Date(e.reportedAt),
        newEstimatedArrival: e.newEstimatedArrival === null ? null : new Date(e.newEstimatedArrival),
        reportNotes: e.reportNotes,
      }),
    );
    return TrackingActivity.reconstruct({
      id: row.id,
      trackingNumber: row.trackingNumber,
      bookingId: row.bookingId,
      events,
      exceptions,
    });
  }
}
