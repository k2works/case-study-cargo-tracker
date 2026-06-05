import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as billingApi from '../api/billingApi';
import InvoiceListPage from './InvoiceListPage';

vi.mock('../api/billingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/billingApi')>(
    '../api/billingApi',
  );
  return {
    ...actual,
    fetchInvoicesPage: vi.fn(),
  };
});

const mkInvoice = (id: string, status: billingApi.BillingStatus = 'CALCULATED'): billingApi.Invoice => ({
  invoiceId: id,
  bookingId: 'B-' + id,
  shipperId: 'S-001',
  basicAmount: '330000',
  discountAmount: '0',
  adjustmentAmount: '0',
  totalAmount: '330000',
  currency: 'JPY',
  billingStatus: status,
  invoiceNumber: status === 'INVOICED' || status === 'PAID' || status === 'OVERDUE'
    ? 'INV-20260901-0001' : null,
  paymentDue: status === 'INVOICED' || status === 'PAID' || status === 'OVERDUE'
    ? '2026-10-01' : null,
  paidAt: null,
  createdAt: '2026-08-20T09:00:00',
  updatedAt: '2026-08-20T09:00:00',
  lines: [],
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/billing']}>
      <Routes>
        <Route path="/billing" element={<InvoiceListPage />} />
        <Route path="/billing/:invoiceId" element={<div data-testid="detail" />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('InvoiceListPage (S22)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US23: 一覧と合計件数を表示する', async () => {
    vi.mocked(billingApi.fetchInvoicesPage).mockResolvedValue({
      items: [mkInvoice('INV-001'), mkInvoice('INV-002', 'INVOICED')],
      totalCount: 2,
      page: 0,
      size: 20,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('INV-001')).toBeInTheDocument();
    });
    expect(screen.getByText('INV-002')).toBeInTheDocument();
    expect(screen.getByText(/合計 2 件/)).toBeInTheDocument();
  });

  it('US23: ステータスフィルタ変更で再フェッチされる', async () => {
    const user = userEvent.setup();
    vi.mocked(billingApi.fetchInvoicesPage)
      .mockResolvedValueOnce({ items: [], totalCount: 0, page: 0, size: 20 })
      .mockResolvedValueOnce({
        items: [mkInvoice('INV-OVR', 'OVERDUE')],
        totalCount: 1,
        page: 0,
        size: 20,
      });

    renderPage();
    await waitFor(() => {
      expect(billingApi.fetchInvoicesPage).toHaveBeenCalledWith(0, 20, undefined);
    });

    await user.selectOptions(screen.getByLabelText('状態フィルタ'), 'OVERDUE');

    await waitFor(() => {
      expect(billingApi.fetchInvoicesPage).toHaveBeenCalledWith(0, 20, 'OVERDUE');
    });
    await waitFor(() => {
      expect(screen.getByText('INV-OVR')).toBeInTheDocument();
    });
  });

  it('US23: 該当なしのとき空メッセージを表示', async () => {
    vi.mocked(billingApi.fetchInvoicesPage).mockResolvedValue({
      items: [],
      totalCount: 0,
      page: 0,
      size: 20,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/該当する請求書がありません/)).toBeInTheDocument();
    });
  });

  it('US23: フェッチエラー時にエラーメッセージを表示', async () => {
    vi.mocked(billingApi.fetchInvoicesPage).mockRejectedValue(
      new Error('請求一覧の取得に失敗しました'),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/請求一覧の取得に失敗しました/)).toBeInTheDocument();
    });
  });
});
