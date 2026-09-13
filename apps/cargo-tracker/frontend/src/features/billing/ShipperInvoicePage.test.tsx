import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ShipperInvoicePage } from './ShipperInvoicePage';

function invoice(over: Record<string, unknown> = {}) {
  return {
    invoiceId: 'INV-20261012-031',
    bookingId: 'B-2026-0902-004',
    shipperId: 'SHP-000001',
    shipperName: '山田商事',
    shipperType: 'CORPORATE',
    shipperTypeLabel: '法人',
    contractNumber: 'CT-0012',
    discountRate: 0.15,
    baseAmount: 1190000,
    discountAmount: 178500,
    adjustmentAmount: 0,
    taxAmount: 0,
    totalAmount: 1011500,
    currency: 'JPY',
    status: 'INVOICED',
    statusLabel: '請求済',
    calculatedAt: '2026-10-10T01:00:00Z',
    quotedAmount: null,
    issuedOn: '2026-10-12',
    dueOn: '2026-11-11',
    paidAt: null,
    overdue: false,
    payments: [],
    lineItems: [
      {
        itemType: 'BASE',
        itemTypeLabel: '基本料金',
        description: '基本料金（3 区間・1,200 kg・一般）',
        amount: 1190000,
        currency: 'JPY',
        basisExceptionId: null,
      },
      {
        itemType: 'DISCOUNT',
        itemTypeLabel: '割引',
        description: '割引（15%・CT-0012）',
        amount: 178500,
        currency: 'JPY',
        basisExceptionId: 'EX-2026-0928-03',
      },
    ],
    ...over,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/shipper/invoices/INV-20261012-031']}>
        <Routes>
          <Route path="/shipper/invoices/:invoiceId" element={<ShipperInvoicePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('S62 自社請求書（荷主）', () => {
  it('D8: 荷主は自社の請求書の金額と支払期限を読める', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice()), { status: 200 }),
    );

    renderPage();

    // **合計は 2 か所に出る**（見出しと明細の最終行）。見出しのほうで確かめる。
    expect(await screen.findByText(/ご請求額 ¥ 1,011,500/)).toBeInTheDocument();
    expect(screen.getByText(/支払期限 2026-11-11/)).toBeInTheDocument();
    expect(screen.getByText('請求済')).toBeInTheDocument();
    // **経理向けの経路を叩かない。** 同じ経路にロールで分岐を足すと、
    // 載せ忘れた分岐ほど無防備になる。
    expect(String(fetchSpy.mock.calls[0]?.[0])).toContain('/billing/shipper-invoices/');
  });

  it('金額の根拠（明細）は読めるが、社内の例外 ID へのリンクは出さない', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice()), { status: 200 }),
    );

    renderPage();

    expect(await screen.findByText(/基本料金（3 区間/)).toBeInTheDocument();
    expect(screen.getByText(/割引（15%・CT-0012）/)).toBeInTheDocument();
    // 荷主には開く先が無い（社内の例外一覧は荷主に開いていない）。
    expect(screen.queryByRole('link', { name: /根拠の例外/ })).not.toBeInTheDocument();
  });

  it('他社の請求書は「ありません」として扱う（存在を教えない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('{}', { status: 404 }),
    );

    renderPage();

    expect(await screen.findByText(/請求書が見つかりません/)).toBeInTheDocument();
  });

  it('自社予約の進み具合へ戻れる（開いた先から戻れないと戻るボタンに頼ることになる）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice()), { status: 200 }),
    );

    renderPage();

    const link = await screen.findByRole('link', { name: '自社予約の進み具合へ戻る' });
    expect(link).toHaveAttribute('href', '/shipper/bookings/B-2026-0902-004');
  });
});
