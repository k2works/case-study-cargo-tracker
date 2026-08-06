import { describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { RoutingQuery, RouteCandidateFinder } from '../../domain/model/route-candidate-finder.js';
import { CarrierMovement, Schedule, Voyage } from '../../domain/model/voyage.js';
import type { ExternalRoutingServicePort } from '../../application/outboundservices/external-routing-service-port.js';
import { FallbackExternalRoutingService } from './fallback-external-routing-service.js';
import { HttpExternalRoutingService } from './http-external-routing-service.js';

describe('FallbackExternalRoutingService', () => {
  it('外部サービスが候補を返した場合はその結果を採用する', async () => {
    const fallback = new RouteCandidateFinder().find(query(), [directVoyage()]);
    const external: ExternalRoutingServicePort = {
      findCandidates: vi.fn().mockResolvedValue(fallback),
    };

    const service = new FallbackExternalRoutingService(external, new RouteCandidateFinder());
    const candidates = await service.findCandidates(query(), [directVoyage()]);

    expect(candidates).toBe(fallback);
    expect(external.findCandidates).toHaveBeenCalledOnce();
  });

  it('外部サービスが空配列を返した場合はドメイン算出へフォールバックする', async () => {
    const service = new FallbackExternalRoutingService(
      { findCandidates: vi.fn().mockResolvedValue([]) },
      new RouteCandidateFinder(),
    );

    const candidates = await service.findCandidates(query(), [directVoyage()]);

    expect(candidates.map((candidate) => candidate.voyageNumbers)).toEqual([['V001']]);
  });

  it('外部サービスが失敗した場合はドメイン算出へフォールバックする', async () => {
    const service = new FallbackExternalRoutingService(
      { findCandidates: vi.fn().mockRejectedValue(new Error('external unavailable')) },
      new RouteCandidateFinder(),
    );

    const candidates = await service.findCandidates(query(), [directVoyage()]);

    expect(candidates.map((candidate) => candidate.voyageNumbers)).toEqual([['V001']]);
  });

  it('HTTP 外部サービス障害時も fallback finder で候補を返す', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('unavailable', { status: 503 })));
    const service = new FallbackExternalRoutingService(
      new HttpExternalRoutingService('https://routing.example.test'),
      new RouteCandidateFinder(),
    );

    const candidates = await service.findCandidates(query(), [directVoyage()]);

    expect(candidates.map((candidate) => candidate.voyageNumbers)).toEqual([['V001']]);
    vi.unstubAllGlobals();
  });
});

function query(): RoutingQuery {
  return RoutingQuery.of({
    origin: 'JPTYO',
    destination: 'SGSIN',
    arrivalDeadline: new Date('2026-09-10T00:00:00Z'),
    cargoType: CargoType.GENERAL,
  });
}

function directVoyage(): Voyage {
  return Voyage.register({
    voyageNumber: 'V001',
    shipName: 'Pacific Star',
    carrierName: 'Oceanic',
    supportedCargoTypes: [CargoType.GENERAL],
    schedule: Schedule.of([
      CarrierMovement.of({
        departureLocation: 'JPTYO',
        arrivalLocation: 'SGSIN',
        departureTime: new Date('2026-09-01T09:00:00Z'),
        arrivalTime: new Date('2026-09-08T08:00:00Z'),
      }),
    ]),
  });
}
