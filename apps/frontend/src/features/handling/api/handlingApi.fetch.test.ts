import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchHandlingByTrackingNumber,
  fetchHandlingPage,
  registerHandling,
  type HandlingActivity,
} from './handlingApi';

const sample: HandlingActivity = {
  activityId: 'A-001',
  bookingId: 'B-001',
  trackingNumber: 'TRK-AB12CD3456',
  originUnlocode: 'JPTYO',
  destinationUnlocode: 'USNYC',
  cargoType: 'GENERAL',
  handlingType: 'RECEIVE',
  occurredAt: '2026-07-20T10:00',
  recordedAt: '2026-07-20T10:00',
  unlocode: 'JPTYO',
  voyageNumber: null,
  handlerId: 'H-001',
  unexpected: false,
};

function ok(json: unknown): Response {
  return new Response(JSON.stringify(json), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function created(json: unknown): Response {
  return new Response(JSON.stringify(json), {
    status: 201,
    headers: { 'Content-Type': 'application/json' },
  });
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

describe('handlingApi fetch クライアント', () => {
  it('fetchHandlingPage: GET /api/v1/handling?page=0&size=20 を呼ぶ', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      ok({ items: [sample], totalCount: 1, page: 0, size: 20 }),
    );

    const result = await fetchHandlingPage(0, 20);

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/handling?page=0&size=20',
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(result.items).toHaveLength(1);
    expect(result.totalCount).toBe(1);
  });

  it('fetchHandlingPage: 失敗時は例外', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(err(500));

    await expect(fetchHandlingPage(0, 20)).rejects.toThrow(/取得に失敗/);
  });

  it('fetchHandlingByTrackingNumber: クエリパラメータが URL エンコードされる', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      ok({ items: [sample], totalCount: 1, page: 0, size: 1 }),
    );

    const result = await fetchHandlingByTrackingNumber('TRK-AB12CD3456');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/handling?trackingNumber=TRK-AB12CD3456',
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(result).toHaveLength(1);
  });

  it('fetchHandlingByTrackingNumber: 失敗時は例外', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(err(404));

    await expect(fetchHandlingByTrackingNumber('TRK-X')).rejects.toThrow(/取得に失敗/);
  });

  it('registerHandling: POST で 201 を受けて activityId を返す', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      created({ activityId: 'A-001' }),
    );

    const result = await registerHandling({
      trackingNumber: 'TRK-AB12CD3456',
      handlingType: 'RECEIVE',
      unlocode: 'JPTYO',
      occurredAt: '2026-07-20T10:00',
      handlerId: 'H-001',
    });

    expect(result.activityId).toBe('A-001');
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/handling',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      }),
    );
  });

  it('registerHandling: サーバーエラーメッセージを引き継ぐ', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      err(422, '不正な状態遷移です'),
    );

    await expect(
      registerHandling({
        trackingNumber: 'TRK-AB12CD3456',
        handlingType: 'CLAIM',
        unlocode: 'USNYC',
        occurredAt: '2026-08-16T14:00',
        handlerId: 'H-001',
      }),
    ).rejects.toThrow('不正な状態遷移です');
  });

  it('registerHandling: 認証トークンがある場合は Authorization ヘッダが付く', async () => {
    localStorage.setItem('auth_token', 'jwt-token-abc');
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      created({ activityId: 'A-002' }),
    );

    await registerHandling({
      trackingNumber: 'TRK-AB12CD3456',
      handlingType: 'RECEIVE',
      unlocode: 'JPTYO',
      occurredAt: '2026-07-20T10:00',
      handlerId: 'H-001',
    });

    const headers = fetchSpy.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer jwt-token-abc');
  });
});
