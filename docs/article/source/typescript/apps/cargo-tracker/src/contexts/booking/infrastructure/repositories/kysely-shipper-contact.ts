import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { ShipperContactAcl } from '../../application/outboundservices/acl/shipper-contact-acl.js';

/**
 * ShipperContactAcl の実装（腐敗防止層）。
 * shipper テーブルの email を直接参照し、Booking から Shipper ドメインへの依存を吸収する。
 * KyselyShipperExistenceChecker と同型の共有 DB 直読パターン（ADR-008 の統制盲点に留意）。
 */
export class KyselyShipperContact implements ShipperContactAcl {
  constructor(private readonly db: AppDatabase) {}

  async findEmailByShipperId(shipperId: number): Promise<string | null> {
    const row = await this.db
      .selectFrom('shipper')
      .select('email')
      .where('id', '=', shipperId)
      .executeTakeFirst();
    return row?.email ?? null;
  }
}
