import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { RouteCandidate, type RoutingQuery } from '../../domain/model/route-candidate-finder.js';
import type { VoyageRepository } from '../../domain/repository/voyage-repository.js';
import type { ExternalRoutingServicePort } from '../outboundservices/external-routing-service-port.js';
import { FindRouteCandidatesService } from './find-route-candidates.service.js';

describe('FindRouteCandidatesService（US08 / Try T2）', () => {
  let voyages: VoyageRepository;
  let routingService: ExternalRoutingServicePort;
  let service: FindRouteCandidatesService;

  beforeEach(() => {
    voyages = { save: vi.fn(), findAll: vi.fn().mockResolvedValue([]), findByVoyageNumber: vi.fn() } as unknown as VoyageRepository;
    routingService = { findCandidates: vi.fn().mockResolvedValue([]) };
    service = new FindRouteCandidatesService(voyages, routingService);
  });

  it('RoutingQuery を組み立て航海を取得して外部経路サービスへ委譲する', async () => {
    const candidate = RouteCandidate.of({
      voyageNumbers: ['V001'],
      transitPorts: [],
      departureTime: new Date('2026-09-01T00:00:00Z'),
      arrivalTime: new Date('2026-09-10T00:00:00Z'),
    });
    vi.mocked(routingService.findCandidates).mockResolvedValue([candidate]);

    const result = await service.find({
      origin: 'JPTYO',
      destination: 'SGSIN',
      arrivalDeadline: '2026-09-30',
      cargoType: CargoType.GENERAL,
    });

    expect(voyages.findAll).toHaveBeenCalled();
    const passedQuery = vi.mocked(routingService.findCandidates).mock.calls[0][0] as RoutingQuery;
    expect(passedQuery.origin.unlocode).toBe('JPTYO');
    expect(passedQuery.destination.unlocode).toBe('SGSIN');
    expect(result).toEqual([candidate]);
  });

  it('不正な貨物種別は GENERAL にフォールバックする', async () => {
    await service.find({ origin: 'JPTYO', destination: 'SGSIN', arrivalDeadline: '2026-09-30', cargoType: 'INVALID' });
    const passedQuery = vi.mocked(routingService.findCandidates).mock.calls[0][0] as RoutingQuery;
    expect(passedQuery.cargoType).toBe(CargoType.GENERAL);
  });
});
