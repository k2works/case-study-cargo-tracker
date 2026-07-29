import type { ExternalRoutingServicePort } from '../../application/outboundservices/external-routing-service-port.js';
import { RouteCandidate, type RoutingQuery } from '../../domain/model/route-candidate-finder.js';
import type { Voyage } from '../../domain/model/voyage.js';

interface ExternalCandidateResponse {
  voyageNumbers: string[];
  transitPorts: string[];
  departureTime: string;
  arrivalTime: string;
}

export class HttpExternalRoutingService implements ExternalRoutingServicePort {
  constructor(private readonly baseUrl: string) {}

  async findCandidates(query: RoutingQuery, _voyages: Voyage[]): Promise<RouteCandidate[]> {
    const response = await fetch(`${this.baseUrl}/route-candidates`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        origin: query.origin.unlocode,
        destination: query.destination.unlocode,
        arrivalDeadline: query.arrivalDeadline.toISOString(),
        cargoType: query.cargoType,
      }),
    });
    if (!response.ok) {
      throw new Error(`外部経路サービスが失敗しました: ${response.status}`);
    }
    const body = (await response.json()) as { candidates?: ExternalCandidateResponse[] };
    return (body.candidates ?? []).map((candidate) =>
      RouteCandidate.of({
        voyageNumbers: candidate.voyageNumbers,
        transitPorts: candidate.transitPorts,
        departureTime: new Date(candidate.departureTime),
        arrivalTime: new Date(candidate.arrivalTime),
      }),
    );
  }
}
