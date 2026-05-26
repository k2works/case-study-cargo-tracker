import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createQuotation, fetchQuotationsPage, fetchQuotation } from './quoteApi';

type FetchMock = ReturnType<typeof vi.fn>;

describe('quoteApi (US01)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  it('見積を作成し見積番号を返す', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({
      ok: true,
      json: async () => ({ quotationId: 'Q-001' }),
    });

    const result = await createQuotation({
      shipperId: 'S-001',
      originUnlocode: 'JPTYO',
      destinationUnlocode: 'USNYC',
      arrivalDeadline: '2026-09-30',
      cargoType: 'GENERAL',
      weightKg: 1500,
      validUntil: '2026-08-31',
      candidates: [
        { itinerarySummary: 'JPTYO → USNYC', estimatedDays: 14, estimatedCost: 850000, estimatedCurrency: 'JPY' },
      ],
    });

    expect(result.quotationId).toBe('Q-001');
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/quotes',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('見積一覧を PageResponse で取得する', async () => {
    const page = { items: [], totalCount: 0, page: 0, size: 20 };
    (fetch as unknown as FetchMock).mockResolvedValue({ ok: true, json: async () => page });

    const result = await fetchQuotationsPage(0, 20);

    expect(result).toEqual(page);
    expect(fetch).toHaveBeenCalledWith('/api/v1/quotes?page=0&size=20', expect.anything());
  });

  it('見積詳細を取得する', async () => {
    const quotation = { quotationId: 'Q-001', candidates: [] };
    (fetch as unknown as FetchMock).mockResolvedValue({ ok: true, json: async () => quotation });

    const result = await fetchQuotation('Q-001');

    expect(result.quotationId).toBe('Q-001');
    expect(fetch).toHaveBeenCalledWith('/api/v1/quotes/Q-001', expect.anything());
  });

  it('取得失敗時は例外を投げる', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({ ok: false, json: async () => ({}) });

    await expect(fetchQuotation('UNKNOWN')).rejects.toThrow();
  });
});
