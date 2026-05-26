import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchShippersPage,
  fetchShippersByEmail,
  fetchShipper,
  registerShipper,
} from './shipperApi';

type FetchMock = ReturnType<typeof vi.fn>;

function mockFetch(body: unknown, ok = true) {
  (fetch as unknown as FetchMock).mockResolvedValue({ ok, json: async () => body });
}

describe('shipperApi (US02/US03)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  it('荷主一覧を PageResponse で取得する', async () => {
    const page = { items: [], totalCount: 0, page: 1, size: 50 };
    mockFetch(page);

    const result = await fetchShippersPage(1, 50);

    expect(result).toEqual(page);
    expect(fetch).toHaveBeenCalledWith('/api/v1/shippers?page=1&size=50', expect.anything());
  });

  it('荷主一覧の取得失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(fetchShippersPage()).rejects.toThrow('荷主の取得に失敗しました');
  });

  it('メールアドレスで荷主を検索する（URL エンコード）', async () => {
    mockFetch([]);

    await fetchShippersByEmail('a+b@example.com');

    const url = (fetch as unknown as FetchMock).mock.calls[0][0] as string;
    expect(url).toContain('/api/v1/shippers/search?email=');
    expect(url).toContain(encodeURIComponent('a+b@example.com'));
  });

  it('荷主検索の失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(fetchShippersByEmail('x@example.com')).rejects.toThrow('荷主の検索に失敗しました');
  });

  it('ID を指定して荷主を取得する', async () => {
    mockFetch({ shipperId: 'S-001' });

    const result = await fetchShipper('S-001');

    expect(result.shipperId).toBe('S-001');
    expect(fetch).toHaveBeenCalledWith('/api/v1/shippers/S-001', expect.anything());
  });

  it('荷主取得の失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(fetchShipper('UNKNOWN')).rejects.toThrow('荷主の取得に失敗しました');
  });

  it('荷主を登録し ID を返す（ログイン済みなら Authorization ヘッダを付与）', async () => {
    localStorage.setItem('auth_token', 'jwt-x');
    mockFetch({ shipperId: 'S-NEW' });

    const result = await registerShipper({
      shipperType: 'INDIVIDUAL',
      name: '荷主',
      addressLine1: '住所',
      city: '市',
      countryCode: 'JP',
      email: 'e@example.com',
      phone: '03-0000-0000',
    });

    expect(result.shipperId).toBe('S-NEW');
    const init = (fetch as unknown as FetchMock).mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(init.headers).toMatchObject({ Authorization: 'Bearer jwt-x' });
  });

  it('荷主登録の失敗時はサーバーメッセージで例外を投げる', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'メールアドレスが重複しています' }),
    });

    await expect(
      registerShipper({
        shipperType: 'CORPORATE',
        name: 'x',
        addressLine1: 'y',
        city: 'z',
        countryCode: 'JP',
        email: 'dup@example.com',
        phone: '03-0000-0000',
        contractNumber: 'CT-1',
        discountRate: 0.1,
      })
    ).rejects.toThrow('メールアドレスが重複しています');
  });
});
