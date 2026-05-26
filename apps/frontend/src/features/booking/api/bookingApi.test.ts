import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchBookingsPage,
  fetchBooking,
  bookCargo,
  handoffBooking,
  confirmBooking,
  cancelBooking,
} from './bookingApi';

type FetchMock = ReturnType<typeof vi.fn>;

function mockFetch(body: unknown, ok = true) {
  (fetch as unknown as FetchMock).mockResolvedValue({ ok, json: async () => body });
}

const sampleBooking = {
  shipperId: 'S-1',
  originUnlocode: 'JPTYO',
  destinationUnlocode: 'USNYC',
  arrivalDeadline: '2027-09-30',
  cargoType: 'GENERAL' as const,
  weightKg: 100,
  quantity: 1,
  productName: '貨物',
};

describe('bookingApi (US04/US05/US06/US13)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  it('予約一覧を PageResponse で取得する', async () => {
    const page = { items: [], totalCount: 0, page: 0, size: 20 };
    mockFetch(page);

    const result = await fetchBookingsPage(0, 20);

    expect(result).toEqual(page);
    expect(fetch).toHaveBeenCalledWith('/api/v1/bookings?page=0&size=20', expect.anything());
  });

  it('予約一覧の取得失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(fetchBookingsPage()).rejects.toThrow('予約一覧の取得に失敗しました');
  });

  it('ID で予約を取得する', async () => {
    mockFetch({ bookingId: 'BK-1' });

    const result = await fetchBooking('BK-1');

    expect(result.bookingId).toBe('BK-1');
    expect(fetch).toHaveBeenCalledWith('/api/v1/bookings/BK-1', expect.anything());
  });

  it('予約取得の失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(fetchBooking('X')).rejects.toThrow('予約の取得に失敗しました');
  });

  it('予約を作成し ID を返す（ログイン済みなら Authorization ヘッダを付与）', async () => {
    localStorage.setItem('auth_token', 'jwt-b');
    mockFetch({ bookingId: 'BK-NEW' });

    const result = await bookCargo(sampleBooking);

    expect(result.bookingId).toBe('BK-NEW');
    const init = (fetch as unknown as FetchMock).mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(init.headers).toMatchObject({ Authorization: 'Bearer jwt-b' });
  });

  it('予約作成の失敗時はサーバーメッセージで例外を投げる', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({
      ok: false,
      json: async () => ({ message: '入力が不正です' }),
    });

    await expect(bookCargo(sampleBooking)).rejects.toThrow('入力が不正です');
  });

  it.each([
    ['handoff', handoffBooking, '/api/v1/bookings/BK-1/handoff'],
    ['confirm', confirmBooking, '/api/v1/bookings/BK-1/confirm'],
    ['cancel', cancelBooking, '/api/v1/bookings/BK-1/cancel'],
  ] as const)('%s は対象 URL に POST する', async (_name, fn, url) => {
    mockFetch({});

    await fn('BK-1');

    const [calledUrl, init] = (fetch as unknown as FetchMock).mock.calls[0];
    expect(calledUrl).toBe(url);
    expect(init.method).toBe('POST');
  });

  it.each([
    [handoffBooking, '経路設計の依頼に失敗しました'],
    [confirmBooking, '予約確定に失敗しました'],
    [cancelBooking, '予約キャンセルに失敗しました'],
  ] as const)('状態遷移の失敗時は例外を投げる', async (fn, msg) => {
    mockFetch({}, false);

    await expect(fn('BK-1')).rejects.toThrow(msg);
  });
});
