import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QuotationCreatePage } from './QuotationCreatePage';
import { QuotationDetailPage } from './QuotationDetailPage';

function quotation(over: Record<string, unknown> = {}) {
  return {
    quotationId: 'Q-0123456789abcdef0123456789abcd',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    arrivalDeadline: '2026-12-01',
    cargoType: 'GENERAL',
    weightKg: 1200,
    estimatedAmount: 510000,
    currency: 'JPY',
    validUntil: '2026-10-28',
    hasDeadlineMeetingCandidate: true,
    createdBy: 'sales01',
    createdAt: '2026-09-28T01:00:00Z',
    candidates: [
      {
        candidateSeq: 1,
        voyageNumbers: 'V-MOL-001 > V-ONE-002',
        transitDays: 20,
        estimatedCost: 510000,
        currency: 'JPY',
        overdueDays: 0,
      },
    ],
    ...over,
  };
}

function renderAt(path: string, element: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/quotations/new" element={element} />
          <Route path="/quotations/:quotationId" element={element} />
          <Route path="/bookings/new" element={<div>予約登録</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('S12 見積作成', () => {
  it('US01 §1: 5 項目を入力して見積を作る', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ quotationId: 'Q-1' }), { status: 200 }),
    );

    renderAt('/quotations/new', <QuotationCreatePage />);

    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('目的地'), 'USNYC');
    await userEvent.type(screen.getByLabelText('希望到着期限'), '2026-12-01');
    await userEvent.type(screen.getByLabelText('重量（kg）'), '1200');
    await userEvent.click(screen.getByRole('button', { name: '見積を作る' }));

    await waitFor(() => {
      const call = fetchSpy.mock.calls.find(([url]) => String(url).includes('/quotations'));
      expect(call).toBeDefined();
      const body = String(call?.[1]?.body);
      expect(body).toContain('"originUnLocode":"JPTYO"');
      expect(body).toContain('"destinationUnLocode":"USNYC"');
      expect(body).toContain('"arrivalDeadline":"2026-12-01"');
      expect(body).toContain('"cargoType":"GENERAL"');
      expect(body).toContain('"weightKg":"1200"');
    });
  });

  it('US01 §6: 危険物を選ぶと危険物申告の入力が出る', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ quotationId: 'Q-1' }), { status: 200 }),
    );

    renderAt('/quotations/new', <QuotationCreatePage />);

    // 既定（一般）では出さない。押せるのに使わない欄を並べない。
    expect(screen.queryByLabelText('IMO クラス')).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS');

    expect(screen.getByLabelText('IMO クラス')).toBeInTheDocument();
    expect(screen.getByLabelText('UN 番号')).toBeInTheDocument();
  });
});

describe('S13 見積詳細', () => {
  it('US01 §3: 候補ごとに経由港・所要日数・概算料金・航海番号が出る', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(quotation()), { status: 200 }),
    );

    renderAt('/quotations/Q-0123456789abcdef0123456789abcd', <QuotationDetailPage />);

    // **候補の行の中**で 4 つが揃っていることを見る。ページのどこかに
    // 港名があるだけでは、候補として読めることの証明にならない。
    const row = (await screen.findByText('V-MOL-001 > V-ONE-002')).closest('tr');
    expect(row).not.toBeNull();
    expect(row).toHaveTextContent('20 日');
    expect(row).toHaveTextContent('¥ 510,000');
    expect(row).toHaveTextContent('JPTYO');
    expect(row).toHaveTextContent('USNYC');
  });

  it('US01 §5: 期限に間に合う候補が無ければ、その旨が出る', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify(quotation({
          hasDeadlineMeetingCandidate: false,
          estimatedAmount: 0,
          candidates: [],
        })),
        { status: 200 },
      ),
    );

    renderAt('/quotations/Q-0123456789abcdef0123456789abcd', <QuotationDetailPage />);

    expect(await screen.findByText(/希望期限に間に合う経路がありません/))
      .toBeInTheDocument();
  });

  it('間に合わない候補には超過日数を添える（「なぜ選べないか」が読める）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify(quotation({
          hasDeadlineMeetingCandidate: false,
          candidates: [
            {
              candidateSeq: 1,
              voyageNumbers: 'V-LATE-001',
              transitDays: 40,
              estimatedCost: 300000,
              currency: 'JPY',
              overdueDays: 5,
            },
          ],
        })),
        { status: 200 },
      ),
    );

    renderAt('/quotations/Q-0123456789abcdef0123456789abcd', <QuotationDetailPage />);

    expect(await screen.findByText(/5 日超過/)).toBeInTheDocument();
  });

  it('この見積で予約へ進める（5 項目を引き継ぐ）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(quotation()), { status: 200 }),
    );

    renderAt('/quotations/Q-0123456789abcdef0123456789abcd', <QuotationDetailPage />);

    const link = await screen.findByRole('link', { name: 'この見積で予約する' });
    // **見積番号を引き継ぐ。** 引き継がないと、予約の側は見積と突き合わせられず
    // 「見積と異なる項目」を知らせられない。
    expect(link.getAttribute('href') ?? '')
      .toContain('quotationId=Q-0123456789abcdef0123456789abcd');
  });

  it('投影が追いつく前は「反映中」を出す（「ありません」に化けさせない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ quotationId: 'Q-1', message: '見積を受け付けました。反映までしばらくお待ちください' }),
        { status: 202 },
      ),
    );

    renderAt('/quotations/Q-1', <QuotationDetailPage />);

    expect(await screen.findByText(/反映までしばらくお待ちください/)).toBeInTheDocument();
  });
});
