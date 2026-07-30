import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { BillingSnapshotAcl } from '../../application/outboundservices/acl/billing-snapshot-acl.js';
import type { BillingSnapshot } from '../../domain/model/billing-snapshot.js';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * BillingSnapshotAcl の実装（腐敗防止層）。
 * Booking 所有の cargo / leg、Shipper 所有の shipper、Tracking 所有の tracking_exception_event を
 * 直読して Billing 固有の BillingSnapshot へ変換する。
 * コード import は検証されるが DB 結合は dependency-cruiser の統制盲点（ADR-008 に記録済み）。
 */
export class KyselyBillingSnapshot implements BillingSnapshotAcl {
  constructor(private readonly db: AppDatabase) {}

  async findByBookingId(bookingId: string): Promise<BillingSnapshot | null> {
    const cargo = await this.db
      .selectFrom('cargo')
      .select([
        'id',
        'shipperId',
        'weight',
        'cargoType',
        'originUnlocode',
        'destinationUnlocode',
        'bookingStatus',
      ])
      .where('bookingId', '=', bookingId)
      .executeTakeFirst();
    if (cargo === undefined) {
      return null;
    }

    const shipper = await this.db
      .selectFrom('shipper')
      .select(['shipperCode', 'name', 'shipperType', 'discountRate'])
      .where('id', '=', cargo.shipperId)
      .executeTakeFirst();

    const legs = await this.db
      .selectFrom('leg')
      .select(['loadTime', 'unloadTime', 'seqNumber'])
      .where('cargoId', '=', cargo.id)
      .orderBy('seqNumber', 'asc')
      .execute();

    const exceptions = await this.db
      .selectFrom('tracking_exception_event as e')
      .innerJoin('tracking_activity as t', 't.id', 'e.trackingId')
      .select(['e.exceptionType', 'e.resolvedAt'])
      .where('t.bookingId', '=', bookingId)
      .execute();

    const shipperType = normalizeShipperType(shipper?.shipperType);
    const rawDiscount = shipper !== undefined ? Number(shipper.discountRate) : 0;

    return {
      bookingId,
      shipperId: shipper?.shipperCode ?? String(cargo.shipperId),
      shipperName: shipper?.name ?? '(不明)',
      shipperType,
      // 個人荷主は割引なし（domain-model ビジネスルール 2）
      discountRate: shipperType === 'CORPORATE' ? rawDiscount : 0,
      weightKg: Number(cargo.weight),
      cargoType: cargo.cargoType,
      transitDays: computeTransitDays(legs),
      origin: cargo.originUnlocode,
      destination: cargo.destinationUnlocode,
      bookingStatus: cargo.bookingStatus,
      activeExceptionTypes: exceptions.filter((e) => e.resolvedAt === null).map((e) => e.exceptionType),
      resolvedExceptionTypes: exceptions.filter((e) => e.resolvedAt !== null).map((e) => e.exceptionType),
    };
  }
}

function normalizeShipperType(value: string | undefined): 'INDIVIDUAL' | 'CORPORATE' {
  return value?.trim().toUpperCase() === 'CORPORATE' ? 'CORPORATE' : 'INDIVIDUAL';
}

/** 旅程の所要日数 = 最後の unload − 最初の load（日数・切り上げ・最低 1 日） */
function computeTransitDays(legs: readonly { loadTime: Date; unloadTime: Date }[]): number {
  if (legs.length === 0) {
    return 1;
  }
  const firstLoad = new Date(legs[0].loadTime).getTime();
  const lastUnload = new Date(legs.at(-1)!.unloadTime).getTime();
  const days = Math.ceil((lastUnload - firstLoad) / MS_PER_DAY);
  return days > 0 ? days : 1;
}
