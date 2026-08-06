import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { ShipperContactPort } from '../../application/outboundservices/acl/shipper-contact-port.js';

/**
 * ShipperContactPort の Kysely 実装（腐敗防止層）。
 * cargo × shipper を直読して予約 ID から荷主メールを解決する。
 * 共有 DB 直読は ADR-008 の統制盲点に留意（参照専用スナップショット）。
 */
export class KyselyShipperContact implements ShipperContactPort {
  constructor(private readonly db: AppDatabase) {}

  async findEmailByBookingId(bookingId: string): Promise<string | null> {
    const row = await this.db
      .selectFrom('cargo')
      .innerJoin('shipper', 'shipper.id', 'cargo.shipperId')
      .select('shipper.email as email')
      .where('cargo.bookingId', '=', bookingId)
      .executeTakeFirst();
    return row?.email ?? null;
  }
}
