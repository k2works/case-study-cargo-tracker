import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { Shipper } from '../../domain/model/shipper.js';
import { isShipperType } from '../../domain/model/value-objects.js';
import type { ShipperRepository } from '../../domain/repository/shipper-repository.js';

/** ShipperRepository の Kysely 実装 */
export class KyselyShipperRepository implements ShipperRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(shipper: Shipper): Promise<number> {
    const inserted = await this.db
      .insertInto('shipper')
      .values({
        shipperCode: shipper.code.value,
        shipperType: shipper.shipperType,
        name: shipper.name.value,
        email: shipper.email.value,
        phone: shipper.phone?.value ?? null,
        contractNumber: shipper.contractNumber?.value ?? null,
        discountRate: shipper.discountRate.value,
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    return inserted.id;
  }

  async findByEmail(email: string): Promise<Shipper | null> {
    const row = await this.db
      .selectFrom('shipper')
      .selectAll()
      .where('email', '=', email)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    if (!isShipperType(row.shipperType)) {
      throw new Error(`不正な荷主種別: ${row.shipperType}`);
    }
    return Shipper.reconstruct({
      id: row.id,
      code: row.shipperCode,
      shipperType: row.shipperType,
      name: row.name,
      email: row.email,
      phone: row.phone,
      address: null,
      contractNumber: row.contractNumber,
      discountRate: Number(row.discountRate),
    });
  }
}
