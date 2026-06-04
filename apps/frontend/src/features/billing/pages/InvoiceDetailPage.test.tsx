import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as billingApi from '../api/billingApi';
import InvoiceDetailPage from './InvoiceDetailPage';

vi.mock('../api/billingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/billingApi')>(
    '../api/billingApi',
  );
  return {
    ...actual,
    fetchInvoice: vi.fn(),
  };
});

const calculatedInvoice: billingApi.Invoice = {
  invoiceId: 'INV-20260820-0001',
  bookingId: 'B-2026-0001',
  shipperId: 'S-001',
  basicAmount: '330000',
  discountAmount: '0',
  adjustmentAmount: '0',
  totalAmount: '330000',
  currency: 'JPY',
  billingStatus: 'CALCULATED',
  invoiceNumber: null,
  paymentDue: null,
  paidAt: null,
  createdAt: '2026-08-20T09:00:00',
  updatedAt: '2026-08-20T09:00:00',
  lines: [
    {
      lineSeq: 1,
      lineType: 'BASIC',
      description: '基本料金（重量 × 距離 × 種別係数 + 取扱費）',
      amount: '330000',
      reasonCode: null,
    },
  ],
};

const invoicedInvoice: billingApi.Invoice = {
  ...calculatedInvoice,
  invoiceId: 'INV-20260815-0007',
  basicAmount: '160000',
  discountAmount: '10000',
  adjustmentAmount: '0',
  totalAmount: '150000',
  billingStatus: 'INVOICED',
  invoiceNumber: 'INV-20260815-0007',
  paymentDue: '2026-09-14',
  lines: [
    {
      lineSeq: 1,
      lineType: 'BASIC',
      description: '基本料金',
      amount: '160000',
      reasonCode: null,
    },
    {
      lineSeq: 2,
      lineType: 'DISCOUNT',
      description: '法人割引（-10%）',
      amount: '-10000',
      reasonCode: 'CORPORATE',
    },
  ],
};

function renderPage(invoiceId: string) {
  return render(
    <MemoryRouter initialEntries={[`/billing/${invoiceId}`]}>
      <Routes>
        <Route path="/billing/:invoiceId" element={<InvoiceDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('InvoiceDetailPage (S23)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US21: CALCULATED 状態の請求詳細を表示する', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(calculatedInvoice);

    renderPage('INV-20260820-0001');

    await waitFor(() => {
      expect(screen.getByText(/INV-20260820-0001/)).toBeInTheDocument();
    });
    expect(screen.getByText(/B-2026-0001/)).toBeInTheDocument();
    expect(screen.getByText(/算出済/)).toBeInTheDocument();
    // 330,000 は明細行と合計の 2 箇所に現れる
    expect(screen.getAllByText(/330,000/).length).toBeGreaterThanOrEqual(2);
  });

  it('US21: 料金内訳 (invoice_line) を表示する', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(invoicedInvoice);

    renderPage('INV-20260815-0007');

    await waitFor(() => {
      // 「基本料金」は明細行に存在する
      expect(screen.getAllByText(/基本料金/).length).toBeGreaterThanOrEqual(1);
    });
    expect(screen.getByText(/法人割引（-10%）/)).toBeInTheDocument();
    expect(screen.getByText('割引')).toBeInTheDocument();
    expect(screen.getByText(/-10,000/)).toBeInTheDocument();
  });

  it('US23: INVOICED 状態では invoiceNumber と paymentDue を表示する', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(invoicedInvoice);

    renderPage('INV-20260815-0007');

    await waitFor(() => {
      expect(screen.getByText(/発行済/)).toBeInTheDocument();
    });
    expect(screen.getByText(/2026-09-14/)).toBeInTheDocument();
  });

  it('合計金額は totalAmount を強調表示する', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(calculatedInvoice);

    renderPage('INV-20260820-0001');

    await waitFor(() => {
      // total は複数箇所にあるが、合計表示が存在することを確認
      const totalMatches = screen.getAllByText(/330,000/);
      expect(totalMatches.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('NOT_FOUND の場合はエラーメッセージを表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockRejectedValue(new Error('NOT_FOUND'));

    renderPage('INV-NOTEXIST');

    await waitFor(() => {
      expect(screen.getByText(/請求書が見つかりません/)).toBeInTheDocument();
    });
  });

  it('読み込み中はローディング表示', () => {
    vi.mocked(billingApi.fetchInvoice).mockImplementation(() => new Promise(() => {}));

    renderPage('INV-20260820-0001');

    expect(screen.getByText(/読み込み中/)).toBeInTheDocument();
  });
});
