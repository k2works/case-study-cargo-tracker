import type { RouteCandidate, RoutingQuery } from '../../domain/model/route-candidate-finder.js';
import type { Voyage } from '../../domain/model/voyage.js';

export interface ExternalRoutingServicePort {
  findCandidates(query: RoutingQuery, voyages: Voyage[]): Promise<RouteCandidate[]>;
}
