import { afterEach, describe, expect, it, vi } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { RoutingQuery } from '../../domain/model/route-candidate-finder.js';
import { HttpExternalRoutingService } from './http-external-routing-service.js';

describe('HttpExternalRoutingService', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('外部経路サービスへ RoutingQuery を POST し候補レスポンスを復元する', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          candidates: [
            {
              voyageNumbers: ['V001', 'V002'],
              transitPorts: ['HKHKG'],
              departureTime: '2026-09-01T09:00:00.000Z',
              arrivalTime: '2026-09-09T08:00:00.000Z',
            },
          ],
        }),
        { status: 200, headers: { 'content-type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const service = new HttpExternalRoutingService('https://routing.example.test');
    const candidates = await service.findCandidates(query(), []);

    expect(fetchMock).toHaveBeenCalledWith(
      'https://routing.example.test/route-candidates',
      expect.objectContaining({
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          origin: 'JPTYO',
          destination: 'SGSIN',
          arrivalDeadline: '2026-09-10T00:00:00.000Z',
          cargoType: CargoType.GENERAL,
        }),
      }),
    );
    expect(candidates[0].voyageNumbers).toEqual(['V001', 'V002']);
    expect(candidates[0].transitPorts).toEqual(['HKHKG']);
  });

  it('非 2xx レスポンスは外部連携エラーとして送出する', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('unavailable', { status: 503 })));

    const service = new HttpExternalRoutingService('https://routing.example.test');

    await expect(service.findCandidates(query(), [])).rejects.toThrow('外部経路サービスが失敗しました');
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
