import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
    fetchPayments: vi.fn().mockResolvedValue([]),
    applyDiscount: vi.fn(),
    issueInvoice: vi.fn(),
    recordPayment: vi.fn(),
    getCircuitBreakerHealth: vi.fn(),
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
    vi.mocked(billingApi.fetchPayments).mockResolvedValue([]);
    vi.mocked(billingApi.getCircuitBreakerHealth).mockResolvedValue({
      name: 'shipperInfo',
      state: 'CLOSED',
      registered: true,
    });
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
    // -10,000 は明細行と割引前後対比の 2 箇所に出現
    expect(screen.getAllByText(/-10,000/).length).toBeGreaterThanOrEqual(1);
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

  // --- IT7 Task 3.4: 割引適用 ---

  it('US22: CALCULATED かつ discountAmount=0 のとき「割引を適用」ボタンを表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(calculatedInvoice);

    renderPage('INV-20260820-0001');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /割引を適用/ })).toBeInTheDocument();
    });
  });

  it('US22: 割引適用済（discountAmount>0）は「割引適用済」バッジを表示しボタンは非表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(invoicedInvoice);

    renderPage('INV-20260815-0007');

    await waitFor(() => {
      expect(screen.getByText(/割引適用済/)).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /割引を適用/ })).not.toBeInTheDocument();
  });

  it('US22: INVOICED 以降は CALCULATED でないため「割引を適用」ボタン非表示', async () => {
    const invoicedNoDiscount: billingApi.Invoice = {
      ...calculatedInvoice,
      billingStatus: 'INVOICED',
      invoiceNumber: 'INV-20260820-0001',
      paymentDue: '2026-09-19',
    };
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(invoicedNoDiscount);

    renderPage('INV-20260820-0001');

    await waitFor(() => {
      expect(screen.getByText(/発行済/)).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /割引を適用/ })).not.toBeInTheDocument();
  });

  it('US22: 割引適用ボタン押下で applyDiscount API を呼び再フェッチする', async () => {
    const user = userEvent.setup();

    // 1 回目: discount なし → ボタン表示
    // 2 回目: discount 適用済（押下後の再フェッチ）
    vi.mocked(billingApi.fetchInvoice)
      .mockResolvedValueOnce(calculatedInvoice)
      .mockResolvedValueOnce({
        ...calculatedInvoice,
        discountAmount: '49500',
        totalAmount: '280500',
        lines: [
          ...calculatedInvoice.lines,
          {
            lineSeq: 2,
            lineType: 'DISCOUNT',
            description: '法人割引（15%）',
            amount: '-49500',
            reasonCode: 'CORPORATE',
          },
        ],
      });
    vi.mocked(billingApi.applyDiscount).mockResolvedValue();
    // IT8 T4.2: Circuit Breaker CLOSED 状態を mock（通常の自動取得 flow）
    vi.mocked(billingApi.getCircuitBreakerHealth).mockResolvedValue({
      name: 'shipperInfo',
      state: 'CLOSED',
      registered: true,
    });

    renderPage('INV-20260820-0001');

    const button = await screen.findByRole('button', { name: /割引を適用/ });
    await user.click(button);

    await waitFor(() => {
      expect(billingApi.applyDiscount).toHaveBeenCalledWith('INV-20260820-0001');
    });
    await waitFor(() => {
      expect(screen.getByText(/割引適用済/)).toBeInTheDocument();
    });
  });

  it('IT8 T4.2: Circuit Breaker OPEN 時はボタン押下で手動入力フォームが表示される', async () => {
    const user = userEvent.setup();

    vi.mocked(billingApi.fetchInvoice)
      .mockResolvedValueOnce(calculatedInvoice)
      .mockResolvedValueOnce({
        ...calculatedInvoice,
        discountAmount: '66000',
        totalAmount: '264000',
        lines: [
          ...calculatedInvoice.lines,
          {
            lineSeq: 2,
            lineType: 'DISCOUNT',
            description: '法人割引（20%）',
            amount: '-66000',
            reasonCode: 'CORPORATE',
          },
        ],
      });
    vi.mocked(billingApi.applyDiscount).mockResolvedValue();
    vi.mocked(billingApi.getCircuitBreakerHealth).mockResolvedValue({
      name: 'shipperInfo',
      state: 'OPEN',
      registered: true,
      failureRate: 100,
    });

    renderPage('INV-20260820-0001');

    const button = await screen.findByRole('button', { name: /割引を適用/ });
    await user.click(button);

    // 手動入力フォームが表示される
    const form = await screen.findByTestId('manual-discount-form');
    expect(form).toHaveTextContent(/Circuit Breaker: OPEN/);

    // 割引率を 0.20 に変更して「手動入力で適用」押下
    const rateInput = screen.getByLabelText('割引率') as HTMLInputElement;
    await user.clear(rateInput);
    await user.type(rateInput, '0.20');
    const submit = screen.getByRole('button', { name: /手動入力で適用/ });
    await user.click(submit);

    // applyDiscount に manualDiscountRate=0.20 が渡る
    await waitFor(() => {
      expect(billingApi.applyDiscount).toHaveBeenCalledWith(
        'INV-20260820-0001',
        0.2,
      );
    });
    await waitFor(() => {
      expect(screen.getByText(/割引適用済/)).toBeInTheDocument();
    });
  });

  it('US22: 割引適用済 invoice は basicAmount → discountAmount → totalAmount の対比を表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(invoicedInvoice);

    renderPage('INV-20260815-0007');

    await waitFor(() => {
      expect(screen.getByText(/割引前後の対比/)).toBeInTheDocument();
    });
    // basicAmount = 160,000
    expect(screen.getAllByText(/160,000/).length).toBeGreaterThanOrEqual(1);
    // discountAmount = 10,000（割引表示、明細行 + 割引前後対比に出現）
    expect(screen.getAllByText(/-10,000/).length).toBeGreaterThanOrEqual(1);
    // totalAmount = 150,000（割引後）
    expect(screen.getAllByText(/150,000/).length).toBeGreaterThanOrEqual(1);
  });

  // --- IT7 Task 4.7: 精算書発行・入金記録ボタン ---

  it('US23: CALCULATED で「精算書を発行」ボタンを表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(calculatedInvoice);

    renderPage('INV-20260820-0001');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /精算書を発行/ })).toBeInTheDocument();
    });
  });

  it('US23: INVOICED では「精算書を発行」ボタンは非表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(invoicedInvoice);

    renderPage('INV-20260815-0007');

    await waitFor(() => {
      expect(screen.getByText(/発行済/)).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /精算書を発行/ })).not.toBeInTheDocument();
  });

  it('US23: 精算書発行ボタン押下で issueInvoice API を呼び再フェッチ', async () => {
    const user = userEvent.setup();
    vi.mocked(billingApi.fetchInvoice)
      .mockResolvedValueOnce(calculatedInvoice)
      .mockResolvedValueOnce({
        ...calculatedInvoice,
        billingStatus: 'INVOICED',
        invoiceNumber: 'INV-20260820-0001',
        paymentDue: '2026-09-19',
      });
    vi.mocked(billingApi.issueInvoice).mockResolvedValue();

    renderPage('INV-20260820-0001');

    const button = await screen.findByRole('button', { name: /精算書を発行/ });
    await user.click(button);

    await waitFor(() => {
      expect(billingApi.issueInvoice).toHaveBeenCalledWith('INV-20260820-0001');
    });
    await waitFor(() => {
      expect(screen.getByText(/発行済/)).toBeInTheDocument();
    });
  });

  it('US23: INVOICED で「入金を記録」ボタンを表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(invoicedInvoice);

    renderPage('INV-20260815-0007');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /入金を記録/ })).toBeInTheDocument();
    });
  });

  it('US23: CALCULATED では「入金を記録」ボタンは非表示', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(calculatedInvoice);

    renderPage('INV-20260820-0001');

    await waitFor(() => {
      expect(screen.getByText(/算出済/)).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /入金を記録/ })).not.toBeInTheDocument();
  });

  it('US23: 入金記録ボタン押下で recordPayment API を呼び totalAmount/currency を渡す', async () => {
    const user = userEvent.setup();
    vi.mocked(billingApi.fetchInvoice)
      .mockResolvedValueOnce(invoicedInvoice)
      .mockResolvedValueOnce({
        ...invoicedInvoice,
        billingStatus: 'PAID',
        paidAt: '2026-09-22T15:00:00',
      });
    vi.mocked(billingApi.recordPayment).mockResolvedValue({ paymentId: 'PAY-001' });

    renderPage('INV-20260815-0007');

    const button = await screen.findByRole('button', { name: /入金を記録/ });
    await user.click(button);

    await waitFor(() => {
      expect(billingApi.recordPayment).toHaveBeenCalledWith('INV-20260815-0007', {
        paidAmount: '150000',
        currency: 'JPY',
        paymentMethod: 'MANUAL',
      });
    });
    await waitFor(() => {
      expect(screen.getByText(/入金済/)).toBeInTheDocument();
    });
  });

  it('US26: PARTIALLY_PAID 状態で残額・累積入金額・部分入金履歴が表示される', async () => {
    const partiallyPaidInvoice: billingApi.Invoice = {
      ...invoicedInvoice,
      invoiceId: 'INV-PP-001',
      totalAmount: '150000',
      paidSoFar: '50000',
      billingStatus: 'PARTIALLY_PAID',
    };
    const partialPayment: billingApi.Payment = {
      paymentId: 'PAY-PP-001',
      invoiceId: 'INV-PP-001',
      paidAmount: '50000',
      currency: 'JPY',
      paidAt: '2026-09-10T14:30:00',
      paymentMethod: 'STRIPE',
      externalReference: 'pi_test_abc123',
      isPartial: true,
    };
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(partiallyPaidInvoice);
    vi.mocked(billingApi.fetchPayments).mockResolvedValue([partialPayment]);

    renderPage('INV-PP-001');

    await screen.findByText(/部分入金状況/);

    expect(screen.getByTestId('partial-payment-balance')).toBeInTheDocument();
    expect(screen.getByTestId('payment-history')).toBeInTheDocument();

    const balanceSection = screen.getByTestId('partial-payment-balance');
    expect(balanceSection).toHaveTextContent('150,000');
    expect(balanceSection).toHaveTextContent('50,000');
    expect(balanceSection).toHaveTextContent('100,000');

    const stripeLink = screen.getByRole('link', { name: /Stripe で表示/ });
    expect(stripeLink).toHaveAttribute(
      'href',
      'https://dashboard.stripe.com/payments/pi_test_abc123',
    );
    expect(stripeLink).toHaveAttribute('target', '_blank');
  });

  it('US26: PARTIALLY_PAID 状態でも入金記録ボタンが表示される', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue({
      ...invoicedInvoice,
      invoiceId: 'INV-PP-002',
      totalAmount: '200000',
      paidSoFar: '80000',
      billingStatus: 'PARTIALLY_PAID',
    });
    vi.mocked(billingApi.fetchPayments).mockResolvedValue([]);

    renderPage('INV-PP-002');

    await screen.findByText(/部分入金状況/);
    expect(screen.getByRole('button', { name: /入金を記録/ })).toBeInTheDocument();
  });

  it('US31: Circuit Breaker OPEN のときページ表示時に「割引率未確定」alert-warning を出す', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(calculatedInvoice);
    vi.mocked(billingApi.getCircuitBreakerHealth).mockResolvedValue({
      name: 'shipperInfo',
      state: 'OPEN',
      registered: true,
      failureRate: 100,
    });

    renderPage('INV-20260820-0001');

    const alert = await screen.findByTestId('discount-pending-alert');
    expect(alert).toHaveAttribute('role', 'alert');
    expect(alert).toHaveTextContent(/割引率が未確定です/);
    expect(alert).toHaveTextContent(/bookingms の障害により最新の割引率を取得できません/);
  });

  it('US31: Circuit Breaker CLOSED のときは「割引率未確定」alert は表示されない', async () => {
    vi.mocked(billingApi.fetchInvoice).mockResolvedValue(calculatedInvoice);

    renderPage('INV-20260820-0001');

    await waitFor(() => {
      expect(screen.getByText(/INV-20260820-0001/)).toBeInTheDocument();
    });
    expect(screen.queryByTestId('discount-pending-alert')).not.toBeInTheDocument();
  });
});
