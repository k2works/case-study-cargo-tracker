import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as quoteApi from '../api/quoteApi';
import QuotationListPage from './QuotationListPage';

vi.mock('../api/quoteApi');

const mockQuotations = [
  {
    quotationId: 'Q-001',
    shipperId: 'S-001',
    originUnlocode: 'JPTYO',
    destinationUnlocode: 'USNYC',
    arrivalDeadline: '2026-09-30',
    cargoType: 'GENERAL',
    weightKg: 1500,
    estimatedAmount: 850000,
    estimatedCurrency: 'JPY',
    validUntil: '2026-08-31',
    status: 'DRAFT',
    candidates: [],
  },
];

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/quotes']}>
      <Routes>
        <Route path="/quotes" element={<QuotationListPage />} />
        <Route path="/quotes/new" element={<div>新規見積フォーム</div>} />
      </Routes>
    </MemoryRouter>
  );
}

function pageOf(items: typeof mockQuotations, totalCount = items.length) {
  return { items, totalCount, page: 0, size: 20 };
}

describe('QuotationListPage (US01)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US01: 見積一覧が表示される', async () => {
    vi.mocked(quoteApi.fetchQuotationsPage).mockResolvedValue(pageOf(mockQuotations));
    renderPage();

    await waitFor(() => expect(screen.getByText('Q-001')).toBeInTheDocument());
    expect(screen.getByText('JPTYO')).toBeInTheDocument();
    expect(screen.getByText('USNYC')).toBeInTheDocument();
    expect(screen.getByText('草稿')).toBeInTheDocument();
  });

  it('US01: 新規見積ボタンを押すと見積フォームへ遷移する', async () => {
    vi.mocked(quoteApi.fetchQuotationsPage).mockResolvedValue(pageOf([]));
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '新規見積' }));

    expect(screen.getByText('新規見積フォーム')).toBeInTheDocument();
  });

  it('US01: 見積が 0 件の場合にメッセージが表示される', async () => {
    vi.mocked(quoteApi.fetchQuotationsPage).mockResolvedValue(pageOf([]));
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('登録されている見積はありません')).toBeInTheDocument()
    );
  });

  it('US01: API 取得失敗時にエラーが表示される', async () => {
    vi.mocked(quoteApi.fetchQuotationsPage).mockRejectedValue(new Error('API ダウン'));
    renderPage();

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('API ダウン'));
  });

  it('US01 / ページネーション: 「次へ」クリックで page=1 が API に渡される', async () => {
    vi.mocked(quoteApi.fetchQuotationsPage)
      .mockResolvedValueOnce({ items: mockQuotations, totalCount: 47, page: 0, size: 20 })
      .mockResolvedValueOnce({ items: [], totalCount: 47, page: 1, size: 20 });
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('Q-001'));
    await user.click(screen.getByRole('button', { name: '次へ' }));

    await waitFor(() => expect(quoteApi.fetchQuotationsPage).toHaveBeenCalledWith(1, 20));
  });
});
