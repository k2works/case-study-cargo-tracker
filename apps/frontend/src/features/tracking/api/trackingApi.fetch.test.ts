import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchTracking,
  fetchTrackingEvents,
  fetchTrackingPage,
  updateTrackingStatus,
  type TrackingEvent,
  type TrackingSummary,
} from './trackingApi';

const summary: TrackingSummary = {
  trackingNumber: 'TRK-AB12CD3456',
  bookingId: 'B-001',
  currentStatus: 'NOT_RECEIVED',
  currentUnlocode: null,
  currentVoyageNumber: null,
  estimatedArrival: null,
  misrouted: false,
  lastEventAt: null,
  deliveredAt: null,
};

const event: TrackingEvent = {
  eventId: 1,
  occurredAt: '2026-07-20T10:00',
  recordedAt: '2026-07-20T10:00',
  eventType: 'TRACKING_INITIALIZED',
  transportStatus: 'NOT_RECEIVED',
  unlocode: null,
  voyageNumber: null,
  handlingType: null,
  source: 'SYSTEM',
  description: null,
};

function ok(json: unknown): Response {
  return new Response(JSON.stringify(json), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function accepted(): Response {
  return new Response(null, { status: 202 });
}

function err(status: number, message = '失敗しました'): Response {
  return new Response(JSON.stringify({ message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('trackingApi fetch クライアント', () => {
  it('fetchTrackingPage: GET /api/v1/tracking?page=N&size=N を呼ぶ', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      ok({ items: [summary], totalCount: 1, page: 0, size: 20 }),
    );

    const result = await fetchTrackingPage(0, 20);

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/tracking?page=0&size=20',
      expect.any(Object),
    );
    expect(result.items).toHaveLength(1);
  });

  it('fetchTrackingPage: 失敗時は例外', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(err(500));
    await expect(fetchTrackingPage()).rejects.toThrow(/取得に失敗/);
  });

  it('fetchTracking: GET /api/v1/tracking/{tn} を呼ぶ', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(ok(summary));

    const result = await fetchTracking('TRK-AB12CD3456');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/tracking/TRK-AB12CD3456',
      expect.any(Object),
    );
    expect(result.trackingNumber).toBe('TRK-AB12CD3456');
  });

  it('fetchTracking: 404 で例外', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(err(404));
    await expect(fetchTracking('TRK-X')).rejects.toThrow(/取得に失敗/);
  });

  it('fetchTrackingEvents: 履歴配列を返す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(ok([event]));

    const result = await fetchTrackingEvents('TRK-AB12CD3456');

    expect(result).toHaveLength(1);
    expect(result[0].eventType).toBe('TRACKING_INITIALIZED');
  });

  it('fetchTrackingEvents: 失敗時は例外', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(err(500));
    await expect(fetchTrackingEvents('TRK-X')).rejects.toThrow(/取得に失敗/);
  });

  it('updateTrackingStatus: POST で 202 を返す（戻り値なし）', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(accepted());

    await updateTrackingStatus('TRK-AB12CD3456', {
      toStatus: 'RECEIVED',
      unlocode: 'JPTYO',
      occurredAt: '2026-07-20T10:00',
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/tracking/TRK-AB12CD3456/status',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      }),
    );
  });

  it('updateTrackingStatus: 422 のとき message を引き継いだ例外', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      err(422, '不正な状態遷移です: NOT_RECEIVED → DELIVERED'),
    );

    await expect(
      updateTrackingStatus('TRK-AB12CD3456', {
        toStatus: 'DELIVERED',
        occurredAt: '2026-08-01T00:00',
      }),
    ).rejects.toThrow(/不正な状態遷移です/);
  });

  it('updateTrackingStatus: 認証トークンがあれば Authorization ヘッダが付く', async () => {
    localStorage.setItem('auth_token', 'jwt-xyz');
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(accepted());

    await updateTrackingStatus('TRK-AB12CD3456', {
      toStatus: 'RECEIVED',
      occurredAt: '2026-07-20T10:00',
    });

    const headers = fetchSpy.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer jwt-xyz');
  });
});
