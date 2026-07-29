import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { LegDraft } from '../../application/commandservices/route-cargo.service.js';
import type {
  RouteCandidateAcl,
  RouteCandidateOption,
  RouteCandidateQuery,
} from '../../application/outboundservices/acl/route-candidate-acl.js';

interface MovementRow {
  voyageNumber: string;
  supportedCargoTypes: string;
  departure: string;
  arrival: string;
  departureDate: Date;
  arrivalDate: Date;
  seqNumber: number;
}

/**
 * 経路候補 ACL の Kysely 実装。
 * voyage / carrier_movement を読み取り、直行および 1 寄港接続の候補を Leg ドラフト付きで組み立てる。
 * Routing のドメイン型には依存せず、Booking の LegDraft へ変換する（読み取り ACL・ADR-008）。
 */
export class KyselyRouteCandidateReader implements RouteCandidateAcl {
  constructor(private readonly db: AppDatabase) {}

  async findCandidates(query: RouteCandidateQuery): Promise<RouteCandidateOption[]> {
    const movements = await this.loadMovements(query.cargoType);
    const deadline = query.arrivalDeadline;
    const direct = this.directCandidates(movements, query);
    const transit = this.transitCandidates(movements, query);
    return [...direct, ...transit]
      .filter((option) => isSameOrBeforeDate(lastArrival(option.legs), deadline))
      .sort(compareOptions);
  }

  private async loadMovements(cargoType: string): Promise<MovementRow[]> {
    const rows = await this.db
      .selectFrom('carrier_movement')
      .innerJoin('voyage', 'voyage.id', 'carrier_movement.voyageId')
      .select([
        'voyage.voyageNumber as voyageNumber',
        'voyage.supportedCargoTypes as supportedCargoTypes',
        'carrier_movement.departureLocationUnlocode as departure',
        'carrier_movement.arrivalLocationUnlocode as arrival',
        'carrier_movement.departureDate as departureDate',
        'carrier_movement.arrivalDate as arrivalDate',
        'carrier_movement.seqNumber as seqNumber',
      ])
      .execute();
    return rows
      .filter((row) => supports(row.supportedCargoTypes, cargoType))
      .map((row) => ({
        ...row,
        departureDate: new Date(row.departureDate),
        arrivalDate: new Date(row.arrivalDate),
      }));
  }

  private directCandidates(movements: MovementRow[], query: RouteCandidateQuery): RouteCandidateOption[] {
    return movements
      .filter((m) => m.departure === query.origin && m.arrival === query.destination)
      .map((m) => buildOption([m], []));
  }

  private transitCandidates(movements: MovementRow[], query: RouteCandidateQuery): RouteCandidateOption[] {
    const options: RouteCandidateOption[] = [];
    const firstLegs = movements.filter((m) => m.departure === query.origin && m.arrival !== query.destination);
    for (const first of firstLegs) {
      const secondLegs = movements.filter(
        (m) =>
          m.departure === first.arrival &&
          m.arrival === query.destination &&
          m.departureDate.getTime() >= first.arrivalDate.getTime(),
      );
      for (const second of secondLegs) {
        options.push(buildOption([first, second], [first.arrival]));
      }
    }
    return options;
  }
}

function buildOption(movements: MovementRow[], transitPorts: string[]): RouteCandidateOption {
  const legs: LegDraft[] = movements.map((m) => ({
    voyageNumber: m.voyageNumber,
    loadLocation: m.departure,
    unloadLocation: m.arrival,
    loadTime: m.departureDate,
    unloadTime: m.arrivalDate,
  }));
  const voyageNumbers = [...new Set(movements.map((m) => m.voyageNumber))];
  const transitDays = daysBetween(legs[0].loadTime, legs[legs.length - 1].unloadTime);
  return {
    id: legs.map((leg) => `${leg.voyageNumber}:${leg.loadLocation}-${leg.unloadLocation}`).join('|'),
    voyageNumbers,
    transitPorts,
    transitDays,
    estimatedCost: 100000 + transitDays * 5000 + (voyageNumbers.length - 1) * 25000,
    legs,
  };
}

function supports(supportedCargoTypes: string, cargoType: string): boolean {
  return supportedCargoTypes
    .split(',')
    .map((s) => s.trim())
    .includes(cargoType);
}

function lastArrival(legs: LegDraft[]): Date {
  return legs[legs.length - 1].unloadTime;
}

/** 期限判定は日付単位（当日着は期限内） */
function isSameOrBeforeDate(target: Date, deadline: Date): boolean {
  const t = Date.UTC(target.getUTCFullYear(), target.getUTCMonth(), target.getUTCDate());
  const d = Date.UTC(deadline.getUTCFullYear(), deadline.getUTCMonth(), deadline.getUTCDate());
  return t <= d;
}

function daysBetween(from: Date, to: Date): number {
  return Math.ceil((to.getTime() - from.getTime()) / (24 * 60 * 60 * 1000));
}

function compareOptions(a: RouteCandidateOption, b: RouteCandidateOption): number {
  if (a.voyageNumbers.length !== b.voyageNumbers.length) {
    return a.voyageNumbers.length - b.voyageNumbers.length;
  }
  if (a.transitDays !== b.transitDays) {
    return a.transitDays - b.transitDays;
  }
  return a.estimatedCost - b.estimatedCost;
}
