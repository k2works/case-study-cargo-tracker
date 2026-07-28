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
  consigneeName: string | null;
  consigneeEmail: string | null;
  consigneeAddress: string | null;
  description: string | null;
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
        'cargo.consigneeName as consigneeName',
        'cargo.consigneeEmail as consigneeEmail',
        'cargo.consigneeAddress as consigneeAddress',
        'cargo.description as description',
      ])
      .where('cargo.bookingId', '=', bookingId)
      .executeTakeFirst();
    return row ?? null;
  }
}
