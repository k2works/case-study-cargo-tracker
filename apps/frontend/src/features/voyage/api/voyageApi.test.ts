import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  searchVoyages,
  fetchVoyages,
  fetchVoyage,
  registerVoyage,
  updateVoyage,
} from './voyageApi';

type FetchMock = ReturnType<typeof vi.fn>;

describe('voyageApi.searchVoyages (US07)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  it('検索条件をクエリパラメータに変換して GET する', async () => {
    const mockVoyages = [{ voyageNumber: 'V001', originUnlocode: 'JPTYO', destUnlocode: 'USNYC' }];
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockVoyages,
    });

    const result = await searchVoyages({
      origin: 'JPTYO',
      destination: 'USNYC',
      departureFrom: '2026-06-01T00:00:00',
      departureTo: '2026-06-30T23:59:00',
      cargoType: 'GENERAL',
    });

    expect(result).toEqual(mockVoyages);
    const url = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toContain('/api/v1/voyages/search?');
    expect(url).toContain('origin=JPTYO');
    expect(url).toContain('destination=USNYC');
    expect(url).toContain('cargoType=GENERAL');
  });

  it('指定されていない条件はクエリに含めない', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => [],
    });

    await searchVoyages({ origin: 'JPTYO' });

    const url = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toContain('origin=JPTYO');
    expect(url).not.toContain('destination=');
    expect(url).not.toContain('cargoType=');
  });

  it('検索失敗時は例外を投げる', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: false });

    await expect(searchVoyages({ origin: 'JPTYO' })).rejects.toThrow();
  });
});

describe('voyageApi CRUD (US24/US25)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  function mockFetch(body: unknown, ok = true) {
    (fetch as unknown as FetchMock).mockResolvedValue({ ok, json: async () => body });
  }

  it('全航海一覧を取得する', async () => {
    mockFetch([{ voyageNumber: 'V001' }]);

    const result = await fetchVoyages();

    expect(result).toHaveLength(1);
    expect(fetch).toHaveBeenCalledWith('/api/v1/voyages', expect.anything());
  });

  it('一覧取得の失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(fetchVoyages()).rejects.toThrow('航海スケジュールの取得に失敗しました');
  });

  it('航海番号で取得する', async () => {
    mockFetch({ voyageNumber: 'V001' });

    const result = await fetchVoyage('V001');

    expect(result.voyageNumber).toBe('V001');
    expect(fetch).toHaveBeenCalledWith('/api/v1/voyages/V001', expect.anything());
  });

  it('航海取得の失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(fetchVoyage('UNKNOWN')).rejects.toThrow('航海スケジュールの取得に失敗しました');
  });

  it('航海を新規登録する（ログイン済みなら Authorization ヘッダを付与）', async () => {
    localStorage.setItem('auth_token', 'jwt-v');
    mockFetch({});

    await registerVoyage({
      voyageNumber: 'V001',
      carrierCode: 'C',
      carrierName: '運送',
      shipName: '船',
      originUnlocode: 'JPTYO',
      destUnlocode: 'USNYC',
      departureDate: '2027-06-01T10:00',
      arrivalDate: '2027-06-15T18:00',
      movements: [],
      acceptedCargoTypes: [],
    });

    const [url, init] = (fetch as unknown as FetchMock).mock.calls[0];
    expect(url).toBe('/api/v1/voyages');
    expect(init.method).toBe('POST');
    expect(init.headers).toMatchObject({ Authorization: 'Bearer jwt-v' });
  });

  it('航海登録の失敗時はサーバーメッセージで例外を投げる', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({
      ok: false,
      json: async () => ({ message: '航海番号が重複しています' }),
    });

    await expect(
      registerVoyage({
        voyageNumber: 'V001',
        carrierCode: 'C',
        carrierName: '運送',
        shipName: '船',
        originUnlocode: 'JPTYO',
        destUnlocode: 'USNYC',
        departureDate: '2027-06-01T10:00',
        arrivalDate: '2027-06-15T18:00',
        movements: [],
        acceptedCargoTypes: [],
      })
    ).rejects.toThrow('航海番号が重複しています');
  });

  it('航海を更新する', async () => {
    mockFetch({});

    await updateVoyage('V001', {
      departureDate: '2027-07-01T10:00',
      arrivalDate: '2027-07-15T18:00',
      movements: [],
      acceptedCargoTypes: [],
    });

    const [url, init] = (fetch as unknown as FetchMock).mock.calls[0];
    expect(url).toBe('/api/v1/voyages/V001');
    expect(init.method).toBe('PUT');
  });

  it('航海更新の失敗時は例外を投げる', async () => {
    mockFetch({}, false);

    await expect(
      updateVoyage('V001', {
        departureDate: '2027-07-01T10:00',
        arrivalDate: '2027-07-15T18:00',
        movements: [],
        acceptedCargoTypes: [],
      })
    ).rejects.toThrow('更新に失敗しました');
  });
});
