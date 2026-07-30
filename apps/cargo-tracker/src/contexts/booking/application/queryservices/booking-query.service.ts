import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';

export interface BookingListItem {
  bookingId: string;
  shipperCode: string;
  origin: string;
  destination: string;
  cargoType: string;
  bookingStatus: string;
  arrivalDeadline: Date;
}

export interface BookingDetail extends BookingListItem {
  weight: string;
  shipperEmail: string;
  consigneeName: string | null;
  consigneeEmail: string | null;
  consigneeAddress: string | null;
  description: string | null;
  trackingNumber: string | null;
  arrivalDeadlineText: string;
  legs: BookingLeg[];
}

export interface BookingLeg {
  voyageNumber: string;
  loadLocation: string;
  unloadLocation: string;
  seqNumber: number;
}

/**
 * 予約クエリサービス（CQRS 読み取り側）。shipper と JOIN して荷主コードを返す。
 */
export class BookingQueryService {
  constructor(private readonly db: AppDatabase) {}

  /** 予約一覧。status 指定時は経路設計待ち等でフィルタする */
  async list(status?: string): Promise<BookingListItem[]> {
    let query = this.db
      .selectFrom('cargo')
      .innerJoin('shipper', 'shipper.id', 'cargo.shipperId')
      .select([
        'cargo.bookingId as bookingId',
        'shipper.shipperCode as shipperCode',
        'cargo.originUnlocode as origin',
        'cargo.destinationUnlocode as destination',
        'cargo.cargoType as cargoType',
        'cargo.bookingStatus as bookingStatus',
        'cargo.arrivalDeadline as arrivalDeadline',
      ])
      .orderBy('cargo.id', 'desc');
    if (status !== undefined && status !== '') {
      query = query.where('cargo.bookingStatus', '=', status);
    }
    return query.execute();
  }

  async findDetail(bookingId: string): Promise<BookingDetail | null> {
    const row = await this.db
      .selectFrom('cargo')
      .innerJoin('shipper', 'shipper.id', 'cargo.shipperId')
      .select([
        'cargo.bookingId as bookingId',
        'shipper.shipperCode as shipperCode',
        'cargo.originUnlocode as origin',
        'cargo.destinationUnlocode as destination',
        'cargo.cargoType as cargoType',
        'cargo.bookingStatus as bookingStatus',
        'cargo.arrivalDeadline as arrivalDeadline',
        'cargo.weight as weight',
        'shipper.email as shipperEmail',
        'cargo.consigneeName as consigneeName',
        'cargo.consigneeEmail as consigneeEmail',
        'cargo.consigneeAddress as consigneeAddress',
        'cargo.description as description',
        'cargo.trackingNumber as trackingNumber',
        'cargo.id as cargoId',
      ])
      .where('cargo.bookingId', '=', bookingId)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    const legRows = await this.db
      .selectFrom('leg')
      .select(['voyageNumber', 'loadLocationUnlocode', 'unloadLocationUnlocode', 'seqNumber'])
      .where('cargoId', '=', row.cargoId)
      .orderBy('seqNumber', 'asc')
      .execute();
    const { cargoId, ...detail } = row;
    void cargoId;
    return {
      ...detail,
      arrivalDeadlineText: toDateText(row.arrivalDeadline),
      legs: legRows.map((leg) => ({
        voyageNumber: leg.voyageNumber,
        loadLocation: leg.loadLocationUnlocode,
        unloadLocation: leg.unloadLocationUnlocode,
        seqNumber: leg.seqNumber,
      })),
    };
  }
}

/** DATE カラム（時刻なし）を YYYY-MM-DD 文字列へ整形する */
function toDateText(value: Date | string): string {
  const date = value instanceof Date ? value : new Date(value);
  return date.toISOString().slice(0, 10);
}
