import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { isCargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from '../../domain/model/cargo.js';
import { isBookingStatus } from '../../domain/model/booking-status.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';

/** CargoRepository の Kysely 実装 */
export class KyselyCargoRepository implements CargoRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(cargo: Cargo): Promise<number> {
    const inserted = await this.db
      .insertInto('cargo')
      .values(this.toRow(cargo))
      .returning('id')
      .executeTakeFirstOrThrow();
    return inserted.id;
  }

  async update(cargo: Cargo): Promise<void> {
    await this.db
      .updateTable('cargo')
      .set({ bookingStatus: cargo.bookingStatus, updatedAt: new Date() })
      .where('bookingId', '=', cargo.bookingId.value)
      .execute();
  }

  async findByBookingId(bookingId: string): Promise<Cargo | null> {
    const row = await this.db
      .selectFrom('cargo')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    if (!isCargoType(row.cargoType)) {
      throw new Error(`不正な貨物種別: ${row.cargoType}`);
    }
    if (!isBookingStatus(row.bookingStatus)) {
      throw new Error(`不正な予約状態: ${row.bookingStatus}`);
    }
    return Cargo.reconstruct({
      id: row.id,
      bookingId: row.bookingId,
      shipperId: row.shipperId,
      cargoType: row.cargoType,
      weightKg: Number(row.weight),
      origin: row.originUnlocode,
      destination: row.destinationUnlocode,
      arrivalDeadline: new Date(row.arrivalDeadline),
      bookingStatus: row.bookingStatus,
      consignee: row.consigneeName
        ? {
            name: row.consigneeName,
            address: row.consigneeAddress ?? '',
            contactEmail: row.consigneeEmail ?? 'unknown@example.com',
          }
        : null,
      dimensions:
        row.dimensionLength !== null && row.dimensionWidth !== null && row.dimensionHeight !== null
          ? {
              length: Number(row.dimensionLength),
              width: Number(row.dimensionWidth),
              height: Number(row.dimensionHeight),
            }
          : undefined,
      quantity: row.quantity ?? undefined,
      description: row.description ?? undefined,
      hazardous:
        row.hazardousClass && row.unNumber && row.properShippingName
          ? {
              hazardousClass: row.hazardousClass,
              unNumber: row.unNumber,
              properShippingName: row.properShippingName,
            }
          : undefined,
      temperature:
        row.minTemperature !== null && row.maxTemperature !== null
          ? {
              minTemperature: Number(row.minTemperature),
              maxTemperature: Number(row.maxTemperature),
              unit: row.temperatureUnit ?? 'CELSIUS',
            }
          : undefined,
    });
  }

  private toRow(cargo: Cargo) {
    return {
      bookingId: cargo.bookingId.value,
      shipperId: cargo.shipperId,
      cargoType: cargo.cargoType,
      weight: cargo.weight.value,
      originUnlocode: cargo.routeSpecification.origin.unlocode,
      destinationUnlocode: cargo.routeSpecification.destination.unlocode,
      arrivalDeadline: cargo.routeSpecification.arrivalDeadline,
      bookingStatus: cargo.bookingStatus,
      consigneeName: cargo.consignee.name,
      consigneeEmail: cargo.consignee.contactEmail,
      consigneeAddress: cargo.consignee.address,
      dimensionLength: cargo.dimensions?.length ?? null,
      dimensionWidth: cargo.dimensions?.width ?? null,
      dimensionHeight: cargo.dimensions?.height ?? null,
      quantity: cargo.quantity ?? null,
      description: cargo.description ?? null,
      hazardousClass: cargo.hazardousDeclaration?.hazardousClass ?? null,
      unNumber: cargo.hazardousDeclaration?.unNumber ?? null,
      properShippingName: cargo.hazardousDeclaration?.properShippingName ?? null,
      minTemperature: cargo.temperatureRequirement?.minTemperature ?? null,
      maxTemperature: cargo.temperatureRequirement?.maxTemperature ?? null,
      temperatureUnit: cargo.temperatureRequirement?.unit ?? null,
    };
  }
}
