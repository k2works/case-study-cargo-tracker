import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { ItinerarySnapshotPort } from '../../application/outboundservices/acl/itinerary-snapshot-port.js';

/**
 * ItinerarySnapshotPort の実装（腐敗防止層・参照専用スナップショット ACL）。
 * Booking 所有の cargo / leg テーブルを直読し、旅程の最終荷降し時刻を推定到着日として返す。
 * コード import は検証されるが DB 結合は dependency-cruiser の統制盲点（ADR-008 に記録済み）。
 */
export class KyselyItinerarySnapshot implements ItinerarySnapshotPort {
  constructor(private readonly db: AppDatabase) {}

  async findExpectedArrivalByBookingId(bookingId: string): Promise<Date | null> {
    const cargo = await this.db
      .selectFrom('cargo')
      .select('id')
      .where('bookingId', '=', bookingId)
      .executeTakeFirst();
    if (cargo === undefined) {
      return null;
    }
    const lastLeg = await this.db
      .selectFrom('leg')
      .select('unloadTime')
      .where('cargoId', '=', cargo.id)
      .orderBy('seqNumber', 'desc')
      .executeTakeFirst();
    if (lastLeg === undefined) {
      return null;
    }
    return new Date(lastLeg.unloadTime);
  }
}
