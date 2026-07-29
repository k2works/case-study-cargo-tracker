import { CargoType, isCargoType } from '../../../../shared/domain/model/cargo-type.js';
import { RouteCandidate, RoutingQuery } from '../../domain/model/route-candidate-finder.js';
import type { VoyageRepository } from '../../domain/repository/voyage-repository.js';
import type { ExternalRoutingServicePort } from '../outboundservices/external-routing-service-port.js';

export interface FindRouteCandidatesQuery {
  origin: string;
  destination: string;
  arrivalDeadline: string;
  cargoType: string;
}

/**
 * 経路候補算出ユースケース（US08）。
 * RoutingQuery の組み立て・航海取得・外部経路サービス委譲を Application 層に集約する（IT4 Try T2）。
 * Presentation（RoutingCandidateController）はこのサービスを呼ぶだけの薄い責務に保つ。
 */
export class FindRouteCandidatesService {
  constructor(
    private readonly voyages: VoyageRepository,
    private readonly routingService: ExternalRoutingServicePort,
  ) {}

  async find(query: FindRouteCandidatesQuery): Promise<RouteCandidate[]> {
    const cargoType = isCargoType(query.cargoType) ? query.cargoType : CargoType.GENERAL;
    const routingQuery = RoutingQuery.of({
      origin: query.origin,
      destination: query.destination,
      arrivalDeadline: new Date(query.arrivalDeadline),
      cargoType,
    });
    const voyages = await this.voyages.findAll();
    return this.routingService.findCandidates(routingQuery, voyages);
  }
}
