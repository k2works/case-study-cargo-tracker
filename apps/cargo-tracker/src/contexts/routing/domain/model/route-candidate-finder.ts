import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Location } from '../../../../shared/domain/model/location.js';
import { isSameOrBeforeDate } from './date-comparison.js';
import { RoutingValidationError } from './routing-validation-error.js';
import type { CarrierMovement, Voyage } from './voyage.js';

interface RoutingQueryProps {
  origin: string;
  destination: string;
  arrivalDeadline: Date;
  cargoType: CargoType;
}

export class RoutingQuery {
  private constructor(
    readonly origin: Location,
    readonly destination: Location,
    readonly arrivalDeadline: Date,
    readonly cargoType: CargoType,
  ) {}

  static of(props: RoutingQueryProps): RoutingQuery {
    const origin = Location.of(props.origin);
    const destination = Location.of(props.destination);
    if (origin.sameAs(destination)) {
      throw new RoutingValidationError('出発地と目的地は異なる必要があります');
    }
    return new RoutingQuery(origin, destination, new Date(props.arrivalDeadline), props.cargoType);
  }
}

export class RouteCandidate {
  private constructor(
    readonly voyageNumbers: string[],
    readonly transitPorts: string[],
    readonly departureTime: Date,
    readonly arrivalTime: Date,
    readonly transitDays: number,
    readonly estimatedCost: number,
  ) {}

  static of(props: {
    voyageNumbers: string[];
    transitPorts: string[];
    departureTime: Date;
    arrivalTime: Date;
  }): RouteCandidate {
    const transitDays = daysBetween(props.departureTime, props.arrivalTime);
    return new RouteCandidate(
      props.voyageNumbers,
      props.transitPorts,
      props.departureTime,
      props.arrivalTime,
      transitDays,
      estimateCost(props.voyageNumbers.length, transitDays),
    );
  }
}

export class RouteCandidateFinder {
  find(query: RoutingQuery, voyages: Voyage[]): RouteCandidate[] {
    const supportedVoyages = voyages.filter((voyage) => voyage.supports(query.cargoType));
    return [...this.directCandidates(query, supportedVoyages), ...this.transitCandidates(query, supportedVoyages)]
      .filter((candidate) => isSameOrBeforeDate(candidate.arrivalTime, query.arrivalDeadline))
      .sort(compareCandidates);
  }

  private directCandidates(query: RoutingQuery, voyages: Voyage[]): RouteCandidate[] {
    return voyages.flatMap((voyage) => {
      const leg = findLeg(voyage, query.origin.unlocode, query.destination.unlocode);
      if (leg === undefined) {
        return [];
      }
      return [
        RouteCandidate.of({
          voyageNumbers: [voyage.voyageNumber.value],
          transitPorts: [],
          departureTime: leg.departureTime,
          arrivalTime: leg.arrivalTime,
        }),
      ];
    });
  }

  private transitCandidates(query: RoutingQuery, voyages: Voyage[]): RouteCandidate[] {
    return voyages.flatMap((firstVoyage) =>
      firstVoyage.schedule.carrierMovements
        .filter((firstLeg) => firstLeg.departureLocation.sameAs(query.origin))
        .flatMap((firstLeg) => connectingCandidates(query, voyages, firstVoyage, firstLeg)),
    );
  }
}

function connectingCandidates(
  query: RoutingQuery,
  voyages: Voyage[],
  firstVoyage: Voyage,
  firstLeg: CarrierMovement,
): RouteCandidate[] {
  return voyages.flatMap((secondVoyage) =>
    secondVoyage.schedule.carrierMovements.flatMap((secondLeg) =>
      canConnect(firstLeg, secondLeg, query)
        ? [
            RouteCandidate.of({
              voyageNumbers: [...new Set([firstVoyage.voyageNumber.value, secondVoyage.voyageNumber.value])],
              transitPorts: [firstLeg.arrivalLocation.unlocode],
              departureTime: firstLeg.departureTime,
              arrivalTime: secondLeg.arrivalTime,
            }),
          ]
        : [],
    ),
  );
}

function canConnect(firstLeg: CarrierMovement, secondLeg: CarrierMovement, query: RoutingQuery): boolean {
  return (
    firstLeg.arrivalLocation.sameAs(secondLeg.departureLocation) &&
    secondLeg.arrivalLocation.sameAs(query.destination) &&
    secondLeg.departureTime.getTime() >= firstLeg.arrivalTime.getTime()
  );
}

function findLeg(voyage: Voyage, origin: string, destination: string): CarrierMovement | undefined {
  return voyage.schedule.carrierMovements.find(
    (movement) =>
      movement.departureLocation.unlocode === origin && movement.arrivalLocation.unlocode === destination,
  );
}

function daysBetween(departureTime: Date, arrivalTime: Date): number {
  const millisPerDay = 24 * 60 * 60 * 1000;
  return Math.ceil((arrivalTime.getTime() - departureTime.getTime()) / millisPerDay);
}

function estimateCost(voyageCount: number, transitDays: number): number {
  return 100000 + transitDays * 5000 + (voyageCount - 1) * 25000;
}

function compareCandidates(a: RouteCandidate, b: RouteCandidate): number {
  if (a.voyageNumbers.length !== b.voyageNumbers.length) {
    return a.voyageNumbers.length - b.voyageNumbers.length;
  }
  if (a.transitDays !== b.transitDays) {
    return a.transitDays - b.transitDays;
  }
  return a.estimatedCost - b.estimatedCost;
}
