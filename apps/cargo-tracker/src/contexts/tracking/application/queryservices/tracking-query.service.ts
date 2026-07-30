import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { ItinerarySnapshotPort } from '../outboundservices/acl/itinerary-snapshot-port.js';

export interface TrackingEventRow {
  eventType: string;
  eventTime: Date;
  location: string | null;
  locationName: string | null;
  voyageNumber: string | null;
}

/** 現在地（最新イベントの場所。イベントなしは null） */
export interface TrackingLocation {
  unlocode: string;
  name: string | null;
}

export interface TrackingDetail {
  trackingNumber: string;
  bookingId: string;
  transportStatus: string;
  currentLocation: TrackingLocation | null;
  /** 推定到着日（旅程の最終荷降し時刻。未確定なら null） */
  expectedArrival: Date | null;
  events: TrackingEventRow[];
}

/** 追跡クエリサービス（CQRS 読み取り側） */
export class TrackingQueryService {
  constructor(
    private readonly db: AppDatabase,
    private readonly itinerary: ItinerarySnapshotPort,
  ) {}

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
      .leftJoin('location', 'location.unlocode', 'tracking_handling_event.locationUnlocode')
      .select([
        'tracking_handling_event.eventType as eventType',
        'tracking_handling_event.eventTime as eventTime',
        'tracking_handling_event.locationUnlocode as locationUnlocode',
        'tracking_handling_event.voyageNumber as voyageNumber',
        'location.name as locationName',
      ])
      .where('tracking_handling_event.trackingId', '=', row.id)
      .orderBy('tracking_handling_event.eventTime', 'desc')
      .execute();
    const mapped: TrackingEventRow[] = events.map((e) => ({
      eventType: e.eventType,
      eventTime: new Date(e.eventTime),
      location: e.locationUnlocode,
      locationName: e.locationName,
      voyageNumber: e.voyageNumber,
    }));
    // 現在地 = 最新イベント（降順先頭）の場所。場所を持つ最初のイベントを採用する
    const latestWithLocation = mapped.find((e) => e.location !== null);
    const currentLocation: TrackingLocation | null =
      latestWithLocation !== undefined && latestWithLocation.location !== null
        ? { unlocode: latestWithLocation.location, name: latestWithLocation.locationName }
        : null;
    const expectedArrival = await this.itinerary.findExpectedArrivalByBookingId(row.bookingId);
    return {
      trackingNumber: row.trackingNumber,
      bookingId: row.bookingId,
      transportStatus: row.transportStatus,
      currentLocation,
      expectedArrival,
      events: mapped,
    };
  }
}
