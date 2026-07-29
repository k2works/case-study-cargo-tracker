import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { isCargoType } from '../../../../shared/domain/model/cargo-type.js';
import type { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { CarrierMovement, Schedule, Voyage } from '../../domain/model/voyage.js';
import type { VoyageRepository } from '../../domain/repository/voyage-repository.js';

export class KyselyVoyageRepository implements VoyageRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(voyage: Voyage): Promise<number> {
    return this.db.transaction().execute(async (trx) => {
      const inserted = await trx
        .insertInto('voyage')
        .values({
          voyageNumber: voyage.voyageNumber.value,
          shipName: voyage.shipName,
          carrierName: voyage.carrierName,
          supportedCargoTypes: voyage.supportedCargoTypes.join(','),
        })
        .returning('id')
        .executeTakeFirstOrThrow();

      await this.insertMovements(trx, inserted.id, voyage);

      return inserted.id;
    });
  }

  async update(voyage: Voyage): Promise<void> {
    await this.db.transaction().execute(async (trx) => {
      const row = await trx
        .updateTable('voyage')
        .set({
          shipName: voyage.shipName,
          carrierName: voyage.carrierName,
          supportedCargoTypes: voyage.supportedCargoTypes.join(','),
          updatedAt: new Date(),
        })
        .where('voyageNumber', '=', voyage.voyageNumber.value)
        .returning('id')
        .executeTakeFirst();

      if (row === undefined) {
        throw new Error(`航海が見つかりません: ${voyage.voyageNumber.value}`);
      }

      await trx.deleteFrom('carrier_movement').where('voyageId', '=', row.id).execute();
      await this.insertMovements(trx, row.id, voyage);
    });
  }

  async findByVoyageNumber(voyageNumber: string): Promise<Voyage | null> {
    const row = await this.db
      .selectFrom('voyage')
      .selectAll()
      .where('voyageNumber', '=', voyageNumber.trim().toUpperCase())
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }

    const movementRows = await this.db
      .selectFrom('carrier_movement')
      .selectAll()
      .where('voyageId', '=', row.id)
      .orderBy('seqNumber')
      .execute();

    return Voyage.reconstruct({
      id: row.id,
      voyageNumber: row.voyageNumber,
      shipName: row.shipName,
      carrierName: row.carrierName,
      supportedCargoTypes: this.parseCargoTypes(row.supportedCargoTypes),
      schedule: Schedule.of(
        movementRows.map((movement) =>
          CarrierMovement.of({
            departureLocation: movement.departureLocationUnlocode,
            arrivalLocation: movement.arrivalLocationUnlocode,
            departureTime: new Date(movement.departureDate),
            arrivalTime: new Date(movement.arrivalDate),
          }),
        ),
      ),
    });
  }

  async findAll(): Promise<Voyage[]> {
    const rows = await this.db.selectFrom('voyage').selectAll().orderBy('voyageNumber').execute();
    const result: Voyage[] = [];
    for (const row of rows) {
      const movementRows = await this.db
        .selectFrom('carrier_movement')
        .selectAll()
        .where('voyageId', '=', row.id)
        .orderBy('seqNumber')
        .execute();
      result.push(this.toVoyage(row, movementRows));
    }
    return result;
  }

  private toVoyage(
    row: {
      id: number;
      voyageNumber: string;
      shipName: string;
      carrierName: string;
      supportedCargoTypes: string;
    },
    movementRows: Array<{
      departureLocationUnlocode: string;
      arrivalLocationUnlocode: string;
      departureDate: Date;
      arrivalDate: Date;
    }>,
  ): Voyage {
    return Voyage.reconstruct({
      id: row.id,
      voyageNumber: row.voyageNumber,
      shipName: row.shipName,
      carrierName: row.carrierName,
      supportedCargoTypes: this.parseCargoTypes(row.supportedCargoTypes),
      schedule: Schedule.of(
        movementRows.map((movement) =>
          CarrierMovement.of({
            departureLocation: movement.departureLocationUnlocode,
            arrivalLocation: movement.arrivalLocationUnlocode,
            departureTime: new Date(movement.departureDate),
            arrivalTime: new Date(movement.arrivalDate),
          }),
        ),
      ),
    });
  }

  private parseCargoTypes(value: string): CargoType[] {
    return value.split(',').map((cargoType) => {
      if (!isCargoType(cargoType)) {
        throw new Error(`不正な貨物種別: ${cargoType}`);
      }
      return cargoType;
    });
  }

  private async insertMovements(
    trx: Pick<AppDatabase, 'insertInto'>,
    voyageId: number,
    voyage: Voyage,
  ): Promise<void> {
    await trx
      .insertInto('carrier_movement')
      .values(
        voyage.schedule.carrierMovements.map((movement) => ({
          voyageId,
          departureLocationUnlocode: movement.departureLocation.unlocode,
          arrivalLocationUnlocode: movement.arrivalLocation.unlocode,
          departureDate: movement.departureTime,
          arrivalDate: movement.arrivalTime,
          seqNumber: movement.seqNumber ?? 0,
        })),
      )
      .execute();
  }
}
