import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { CargoSnapshotAcl } from '../../application/outboundservices/acl/cargo-snapshot-acl.js';
import type { CargoSnapshot } from '../../domain/model/cargo-snapshot.js';

/**
 * CargoSnapshotAcl の実装（腐敗防止層）。
 * Booking 所有の cargo / leg テーブルを直読して Handling 固有の CargoSnapshot へ変換する。
 * コード import は検証されるが DB 結合は dependency-cruiser の統制盲点（ADR-008 に記録済み）。
 */
export class KyselyCargoSnapshot implements CargoSnapshotAcl {
  constructor(private readonly db: AppDatabase) {}

  async findByTrackingNumber(trackingNumber: string): Promise<CargoSnapshot | null> {
    const cargo = await this.db
      .selectFrom('cargo')
      .select(['id', 'bookingId', 'originUnlocode', 'destinationUnlocode', 'routingStatus'])
      .where('trackingNumber', '=', trackingNumber)
      .executeTakeFirst();
    if (cargo === undefined) {
      return null;
    }
    const legs = await this.db
      .selectFrom('leg')
      .select(['loadLocationUnlocode', 'unloadLocationUnlocode', 'voyageNumber'])
      .where('cargoId', '=', cargo.id)
      .orderBy('seqNumber', 'asc')
      .execute();
    return {
      bookingId: cargo.bookingId,
      origin: cargo.originUnlocode,
      destination: cargo.destinationUnlocode,
      routingStatus: cargo.routingStatus,
      itineraryLegs: legs.map((leg) => ({
        loadLocation: leg.loadLocationUnlocode,
        unloadLocation: leg.unloadLocationUnlocode,
        voyageNumber: leg.voyageNumber,
      })),
    };
  }
}
