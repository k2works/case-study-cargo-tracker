import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { CARGO_TYPE_LABELS, isCargoType } from '../../../../shared/domain/model/cargo-type.js';

export interface VoyageListItem {
  voyageNumber: string;
  shipName: string;
  carrierName: string;
  supportedCargoTypes: string;
  departureLocation: string;
  arrivalLocation: string;
  departureTime: Date;
  arrivalTime: Date;
  transitPorts: string[];
}

export interface VoyageSearchCriteria {
  origin?: string;
  destination?: string;
  cargoType?: string;
}

export class VoyageQueryService {
  constructor(private readonly db: AppDatabase) {}

  async list(criteria: VoyageSearchCriteria = {}): Promise<VoyageListItem[]> {
    let query = this.db.selectFrom('voyage').selectAll().orderBy('voyageNumber');
    if (criteria.cargoType && isCargoType(criteria.cargoType)) {
      query = query.where('supportedCargoTypes', 'like', `%${criteria.cargoType}%`);
    }
    const voyages = await query.execute();
    const result: VoyageListItem[] = [];
    for (const voyage of voyages) {
      const movements = await this.movementsFor(voyage.id);
      if (movements.length === 0) {
        continue;
      }
      const first = movements[0];
      const last = movements[movements.length - 1];
      result.push({
        voyageNumber: voyage.voyageNumber,
        shipName: voyage.shipName,
        carrierName: voyage.carrierName,
        supportedCargoTypes: voyage.supportedCargoTypes
          .split(',')
          .filter(isCargoType)
          .map((cargoType) => CARGO_TYPE_LABELS[cargoType])
          .join('、'),
        departureLocation: first.departureLocationUnlocode,
        arrivalLocation: last.arrivalLocationUnlocode,
        departureTime: new Date(first.departureDate),
        arrivalTime: new Date(last.arrivalDate),
        transitPorts: movements.slice(0, -1).map((movement) => movement.arrivalLocationUnlocode),
      });
    }
    return result.filter((voyage) => matchesCriteria(voyage, criteria));
  }

  async find(voyageNumber: string): Promise<VoyageListItem | null> {
    return (await this.list()).find((voyage) => voyage.voyageNumber === voyageNumber.toUpperCase()) ?? null;
  }

  private async movementsFor(voyageId: number) {
    return this.db
      .selectFrom('carrier_movement')
      .selectAll()
      .where('voyageId', '=', voyageId)
      .orderBy('seqNumber')
      .execute();
  }
}

function matchesCriteria(voyage: VoyageListItem, criteria: VoyageSearchCriteria): boolean {
  const origin = criteria.origin?.trim().toUpperCase();
  const destination = criteria.destination?.trim().toUpperCase();
  return (
    (origin === undefined || origin === '' || voyage.departureLocation === origin) &&
    (destination === undefined || destination === '' || voyage.arrivalLocation === destination)
  );
}
