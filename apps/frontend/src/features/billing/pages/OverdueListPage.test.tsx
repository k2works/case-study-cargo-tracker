import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as billingApi from '../api/billingApi';
import OverdueListPage from './OverdueListPage';

vi.mock('../api/billingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/billingApi')>(
    '../api/billingApi',
  );
  return {
    ...actual,
    fetchOverdueInvoices: vi.fn(),
  };
});

const overdueInvoice: billingApi.Invoice = {
  invoiceId: 'INV-OVR-001',
  bookingId: 'B-OVR-001',
  shipperId: 'S-001',
  basicAmount: '330000',
  discountAmount: '0',
  adjustmentAmount: '0',
  totalAmount: '330000',
  currency: 'JPY',
  billingStatus: 'INVOICED',
  invoiceNumber: 'INV-20260801-0001',
  paymentDue: '2026-08-31',
  paidAt: null,
  createdAt: '2026-08-01T09:00:00',
  updatedAt: '2026-08-01T09:00:00',
  lines: [],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/billing/overdue']}>
      <Routes>
        <Route path="/billing/overdue" element={<OverdueListPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('OverdueListPage (S25)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US23: 督促対象を表示する', async () => {
    vi.mocked(billingApi.fetchOverdueInvoices).mockResolvedValue({
      items: [overdueInvoice],
      totalCount: 1,
      page: 0,
      size: 1,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('INV-OVR-001')).toBeInTheDocument();
    });
    expect(screen.getByText('INV-20260801-0001')).toBeInTheDocument();
    expect(screen.getByText('2026-08-31')).toBeInTheDocument();
    expect(screen.getByText(/合計 1 件/)).toBeInTheDocument();
  });

  it('US23: 督促対象が無いときは green メッセージ', async () => {
    vi.mocked(billingApi.fetchOverdueInvoices).mockResolvedValue({
      items: [],
      totalCount: 0,
      page: 0,
      size: 0,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/督促対象の請求書はありません/)).toBeInTheDocument();
    });
  });

  it('US23: エラー時にエラーメッセージを表示', async () => {
    vi.mocked(billingApi.fetchOverdueInvoices).mockRejectedValue(
      new Error('督促対象の取得に失敗しました'),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/督促対象の取得に失敗しました/)).toBeInTheDocument();
    });
  });
});
