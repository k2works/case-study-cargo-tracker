import type { ExternalRoutingServicePort } from '../../application/outboundservices/external-routing-service-port.js';
import type { RouteCandidate, RoutingQuery } from '../../domain/model/route-candidate-finder.js';
import { RouteCandidateFinder } from '../../domain/model/route-candidate-finder.js';
import type { Voyage } from '../../domain/model/voyage.js';

export class FallbackExternalRoutingService implements ExternalRoutingServicePort {
  constructor(
    private readonly external: ExternalRoutingServicePort,
    private readonly fallbackFinder: RouteCandidateFinder,
  ) {}

  async findCandidates(query: RoutingQuery, voyages: Voyage[]): Promise<RouteCandidate[]> {
    try {
      const externalCandidates = await this.external.findCandidates(query, voyages);
      if (externalCandidates.length > 0) {
        return externalCandidates;
      }
    } catch {
      // 外部 routing service の障害時は Routing Context のドメイン算出へフォールバックする。
    }
    return this.fallbackFinder.find(query, voyages);
  }
}
