import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { InvoiceListPage } from './InvoiceListPage';
import { InvoiceDetailPage } from './InvoiceDetailPage';
import { useAuthStore } from '@/shared/auth/authStore';

function summary(over: Record<string, unknown> = {}) {
  return {
    invoiceId: 'INV-20260928-1a2b3c4d',
    bookingId: 'B-2026-0902-004',
    shipperId: 'SHP-000001',
    shipperName: '山田商事',
    shipperTypeLabel: '法人',
    status: 'CALCULATED',
    statusLabel: '算出済',
    totalAmount: 433500,
    currency: 'JPY',
    calculatedAt: '2026-09-28T01:00:00Z',
    ...over,
  };
}

function invoice(over: Record<string, unknown> = {}) {
  return {
    invoiceId: 'INV-20260928-1a2b3c4d',
    bookingId: 'B-2026-0902-004',
    shipperId: 'SHP-000001',
    shipperName: '山田商事',
    shipperType: 'CORPORATE',
    shipperTypeLabel: '法人',
    contractNumber: 'CT-0012',
    discountRate: 0.15,
    baseAmount: 510000,
    discountAmount: 76500,
    adjustmentAmount: 0,
    taxAmount: 0,
    totalAmount: 433500,
    currency: 'JPY',
    status: 'CALCULATED',
    statusLabel: '算出済',
    calculatedAt: '2026-09-28T01:00:00Z',
    quotedAmount: null,
    lineItems: [
      {
        itemType: 'BASE',
        itemTypeLabel: '基本料金',
        description: '基本料金（2 区間・近海 2.5 + 遠洋 6.0・1,200 kg・一般 1.0）',
        amount: 510000,
        currency: 'JPY',
        basisExceptionId: null,
      },
      {
        itemType: 'DISCOUNT',
        itemTypeLabel: '割引',
        description: '割引（15%・CT-0012）',
        amount: 76500,
        currency: 'JPY',
        basisExceptionId: null,
      },
      {
        itemType: 'TAX',
        itemTypeLabel: '消費税',
        description: '消費税（輸出免税）',
        amount: 0,
        currency: 'JPY',
        basisExceptionId: null,
      },
    ],
    ...over,
  };
}

function renderAt(path: string, element: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={path.split('?')[0] as string} element={element} />
          <Route path="/invoices" element={<h1>請求一覧</h1>} />
          <Route path="/tracking/exceptions" element={<h1>例外一覧</h1>} />
          <Route path="/bookings/:bookingId" element={<h1>予約詳細</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({
    user: { username: 'accountant01', roles: ['ROLE_ACCOUNTANT'], token: 't' },
  });
});
afterEach(() => vi.restoreAllMocks());

describe('S60 請求一覧', () => {
  it('既定で入金済・取消を外して問い合わせる（決着したものが混ざると一覧が信用されない）',
    async () => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify({ items: [summary()], total: 1 }), { status: 200 }),
      );

      renderAt('/invoices', <InvoiceListPage />);

      expect(await screen.findByText('INV-20260928-1a2b3c4d')).toBeInTheDocument();
      expect(String(fetchSpy.mock.calls[0]?.[0])).toContain('includeSettled=false');
    });

  it('金額と荷主種別が読める（列挙名を出さない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [summary()], total: 1 }), { status: 200 }),
    );

    renderAt('/invoices', <InvoiceListPage />);

    expect(await screen.findByText('¥ 433,500')).toBeInTheDocument();
    expect(screen.getByText('算出済')).toBeInTheDocument();
    expect(screen.getByText(/山田商事（法人）/)).toBeInTheDocument();
  });

  it('鍵を破棄した荷主は「（削除済）」と出す（空欄だと取得漏れに見える）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [summary({ shipperName: null })], total: 1 }),
        { status: 200 }),
    );

    renderAt('/invoices', <InvoiceListPage />);

    expect(await screen.findByText(/（削除済）/)).toBeInTheDocument();
  });

  it('1 件も無ければ、どうすれば増えるかを書く（空の表を黙って出さない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 }),
    );

    renderAt('/invoices', <InvoiceListPage />);

    expect(await screen.findByText(/引取が完了すると自動で作られます/)).toBeInTheDocument();
  });

  it('S22 から来たときは、その予約の請求書だけが出る（着いた先が絞り込まれている）',
    async () => {
      // **Try T1。** href を検査するだけでは、着いた先が期待の絞り込みで
      // 開くかを判別しない。予約を指して来たら、決着したものも含めて
      // その予約の分だけを出す——「済んでいる」ことが知りたくて来ている。
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify({ items: [summary()], total: 1 }), { status: 200 }),
      );

      renderAt('/invoices?bookingId=B-2026-0902-004', <InvoiceListPage />);

      await screen.findByText('INV-20260928-1a2b3c4d');
      const url = String(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain('bookingId=B-2026-0902-004');
      expect(url).toContain('includeSettled=true');
      expect(screen.getByText(/予約 B-2026-0902-004 の請求書だけを出しています/))
        .toBeInTheDocument();
    });

  it('行から請求詳細（S61）へ飛べる', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [summary()], total: 1 }), { status: 200 }),
    );

    renderAt('/invoices', <InvoiceListPage />);

    const link = await screen.findByRole('link', { name: 'INV-20260928-1a2b3c4d' });
    expect(link).toHaveAttribute('href', '/invoices/INV-20260928-1a2b3c4d');
  });
});

describe('S61 請求詳細', () => {
  it('US21 §2: 基本料金の根拠が並ぶ（金額だけでは確かめようがない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice()), { status: 200 }),
    );

    renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);

    expect(await screen.findByText(/2 区間・近海 2.5 \+ 遠洋 6.0・1,200 kg・一般 1.0/))
      .toBeInTheDocument();
  });

  it('US22 §4: 割引の根拠に割引率と契約番号が出る', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice()), { status: 200 }),
    );

    renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);

    expect(await screen.findByText(/割引（15%・CT-0012）/)).toBeInTheDocument();
    expect(screen.getByText('− ¥ 76,500')).toBeInTheDocument();
  });

  it('D5: 輸出免税は「消費税 ¥ 0」として読める', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice()), { status: 200 }),
    );

    renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);

    expect(await screen.findByText('消費税（輸出免税）')).toBeInTheDocument();
    expect(screen.getByText('¥ 0')).toBeInTheDocument();
  });

  it('D7: 調整行から根拠の例外へ飛べる（ID を出すだけにしない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice({
        adjustmentAmount: -10000,
        totalAmount: 423500,
        lineItems: [
          ...invoice().lineItems,
          {
            itemType: 'ADJUSTMENT',
            itemTypeLabel: '調整',
            description: '誤配による再設計',
            amount: -10000,
            currency: 'JPY',
            basisExceptionId: 'EX-2026-0928-03',
          },
        ],
      })), { status: 200 }),
    );

    renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);

    const link = await screen.findByRole('link', { name: /根拠の例外（EX-2026-0928-03）/ });
    expect(link).toHaveAttribute(
      'href', '/tracking/exceptions?exceptionId=EX-2026-0928-03');
  });

  it('見積を経ない予約では、概算行も差額も出さない（注 N5）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(invoice()), { status: 200 }),
    );

    renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);

    await screen.findByText(/2 区間/);
    expect(screen.queryByText(/見積時の概算/)).not.toBeInTheDocument();
    expect(screen.queryByText(/差額/)).not.toBeInTheDocument();
  });

  it('US21 §6: 調整を送ると送信中を出し、理由と根拠を一緒に送る', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(invoice()), { status: 200 }));

    renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);
    await screen.findByText(/2 区間/);

    await userEvent.type(screen.getByLabelText('調整額'), '-10000');
    await userEvent.type(screen.getByLabelText('理由'), '遅延の補償');
    await userEvent.type(screen.getByLabelText('根拠の例外 ID（任意）'), 'EX-1');
    await userEvent.click(screen.getByRole('button', { name: '調整を入れる' }));

    await waitFor(() => {
      const call = fetchSpy.mock.calls.find(
        ([url]) => String(url).includes('/adjustments'));
      expect(call).toBeDefined();
      expect(String(call?.[1]?.body)).toContain('"reason":"遅延の補償"');
      expect(String(call?.[1]?.body)).toContain('"basisExceptionId":"EX-1"');
      expect(String(call?.[1]?.body)).toContain('"amount":-10000');
    });
  });

  it('算出済でない請求書には調整の口を出さない（押せるのに断られる操作を並べない）',
    async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify(invoice({ status: 'PAID', statusLabel: '入金済' })),
          { status: 200 }),
      );

      renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);

      await screen.findByText(/2 区間/);
      expect(screen.queryByRole('button', { name: '調整を入れる' })).not.toBeInTheDocument();
    });

  it('断られた理由をそのまま出す（画面で言い換えない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (String(url).includes('/adjustments')) {
        return new Response(
          JSON.stringify({ code: 'BUSINESS_RULE_VIOLATION', message: '調整額が 0 円です' }),
          { status: 422 });
      }
      return new Response(JSON.stringify(invoice()), { status: 200 });
    });

    renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);
    await screen.findByText(/2 区間/);
    await userEvent.type(screen.getByLabelText('調整額'), '0');
    await userEvent.type(screen.getByLabelText('理由'), '動かない調整');
    await userEvent.click(screen.getByRole('button', { name: '調整を入れる' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('調整額が 0 円です');
  });

  it('一覧へ戻る口がある（開いた先から戻れないと、戻るボタンに頼ることになる）',
    async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify(invoice()), { status: 200 }),
      );

      renderAt('/invoices/INV-20260928-1a2b3c4d', <InvoiceDetailPage />);

      expect(await screen.findByRole('link', { name: '請求一覧へ戻る' }))
        .toHaveAttribute('href', '/invoices');
    });
});
