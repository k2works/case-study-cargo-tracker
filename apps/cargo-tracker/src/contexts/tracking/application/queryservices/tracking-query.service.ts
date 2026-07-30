import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';

export interface TrackingEventRow {
  eventType: string;
  eventTime: Date;
  location: string | null;
  voyageNumber: string | null;
}

export interface TrackingDetail {
  trackingNumber: string;
  bookingId: string;
  transportStatus: string;
  events: TrackingEventRow[];
}

/** 追跡クエリサービス（CQRS 読み取り側） */
export class TrackingQueryService {
  constructor(private readonly db: AppDatabase) {}

  async findDetail(trackingNumber: string): Promise<TrackingDetail | null> {
    const row = await this.db
      .selectFrom('tracking_activity')
      .select(['id', 'trackingNumber', 'bookingId', 'transportStatus'])
      .where('trackingNumber', '=', trackingNumber)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    const events = await this.db
      .selectFrom('tracking_handling_event')
      .select(['eventType', 'eventTime', 'locationUnlocode', 'voyageNumber'])
      .where('trackingId', '=', row.id)
      .orderBy('eventTime', 'desc')
      .execute();
    return {
      trackingNumber: row.trackingNumber,
      bookingId: row.bookingId,
      transportStatus: row.transportStatus,
      events: events.map((e) => ({
        eventType: e.eventType,
        eventTime: new Date(e.eventTime),
        location: e.locationUnlocode,
        voyageNumber: e.voyageNumber,
      })),
    };
  }
}
