import { describe, it, expect, vi, beforeEach } from 'vitest';
import { searchVoyages } from './voyageApi';

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
