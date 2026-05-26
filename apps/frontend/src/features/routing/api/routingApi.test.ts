import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchRouteDesignRequests,
  calculateRoutes,
  selectRoute,
  confirmRoute,
} from './routingApi';

const fetchMock = () => fetch as unknown as ReturnType<typeof vi.fn>;

describe('routingApi (US08/US09/US11)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  it('US08: 経路設計待ちリストを到着期限の昇順に並べて返す', async () => {
    fetchMock().mockResolvedValue({
      ok: true,
      json: async () => [
        { bookingId: 'B-2', arrivalDeadline: '2026-09-01' },
        { bookingId: 'B-1', arrivalDeadline: '2026-08-01' },
        { bookingId: 'B-3', arrivalDeadline: '2026-10-01' },
      ],
    });

    const result = await fetchRouteDesignRequests();

    expect(result.map((r) => r.bookingId)).toEqual(['B-1', 'B-2', 'B-3']);
    expect(fetchMock().mock.calls[0][0]).toBe('/api/v1/routes/design-requests');
  });

  it('US08: 経路候補を POST /calculate で算出する', async () => {
    const candidates = [{ sequence: 1, voyageNumbers: ['V-DIRECT'], recommended: true }];
    fetchMock().mockResolvedValue({ ok: true, json: async () => candidates });

    const result = await calculateRoutes('B-001');

    expect(result).toEqual(candidates);
    expect(fetchMock().mock.calls[0][0]).toBe('/api/v1/routes/B-001/calculate');
    expect(fetchMock().mock.calls[0][1].method).toBe('POST');
  });

  it('US09: 経路候補を選択番号付きで確定する', async () => {
    fetchMock().mockResolvedValue({ ok: true, json: async () => ({ sequence: 2 }) });

    await selectRoute('B-001', 2);

    const [url, init] = fetchMock().mock.calls[0];
    expect(url).toBe('/api/v1/routes/B-001/select');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ sequence: 2 });
  });

  it('US11: 確定経路を予約に紐付ける', async () => {
    fetchMock().mockResolvedValue({ ok: true, json: async () => ({}) });

    await confirmRoute('B-001', 1);

    const [url, init] = fetchMock().mock.calls[0];
    expect(url).toBe('/api/v1/routes/B-001/confirm');
    expect(JSON.parse(init.body)).toEqual({ sequence: 1 });
  });

  it('US08: 算出失敗時は例外を投げる', async () => {
    fetchMock().mockResolvedValue({ ok: false });

    await expect(calculateRoutes('B-001')).rejects.toThrow();
  });
});
