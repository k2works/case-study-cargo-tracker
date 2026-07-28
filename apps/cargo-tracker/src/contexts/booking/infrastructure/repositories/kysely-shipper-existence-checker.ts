import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { ShipperExistenceChecker } from '../../application/outboundservices/acl/shipper-existence-checker.js';

/**
 * ShipperExistenceChecker の実装（腐敗防止層）。
 * shipper テーブルを直接参照し、Booking から Shipper ドメインへの依存を吸収する。
 * Booking Context は Shipper Context のドメインモデルを import しない（BC 独立性）。
 */
export class KyselyShipperExistenceChecker implements ShipperExistenceChecker {
  constructor(private readonly db: AppDatabase) {}

  async resolveShipperId(shipperCode: string): Promise<number | null> {
    const row = await this.db
      .selectFrom('shipper')
      .select('id')
      .where('shipperCode', '=', shipperCode)
      .executeTakeFirst();
    return row?.id ?? null;
  }
}
