import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import * as quoteApi from '../api/quoteApi';
import QuotationDetailPage from './QuotationDetailPage';

vi.mock('../api/quoteApi');

/** 予約化遷移時に渡される navigation state（fromQuotation）を可視化するプローブ。 */
function BookingFormProbe() {
  const location = useLocation();
  const preset = (location.state as { fromQuotation?: Record<string, unknown> } | null)?.fromQuotation;
  return (
    <div>
      新規予約フォーム
      {preset && <span data-testid="preset">{JSON.stringify(preset)}</span>}
    </div>
  );
}

const mockQuotation = {
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
  candidates: [
    { candidateSeq: 1, estimatedDays: 14, estimatedCost: 850000, estimatedCurrency: 'JPY', itinerarySummary: 'JPTYO → USNYC（V001）' },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/quotes/Q-001']}>
      <Routes>
        <Route path="/quotes/:quotationId" element={<QuotationDetailPage />} />
        <Route path="/quotes" element={<div>見積一覧</div>} />
        <Route path="/bookings/new" element={<BookingFormProbe />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('QuotationDetailPage (US01)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US01: 見積詳細が表示される', async () => {
    vi.mocked(quoteApi.fetchQuotation).mockResolvedValue(mockQuotation);
    renderPage();

    await waitFor(() => expect(screen.getByText('見積 Q-001')).toBeInTheDocument());
    expect(screen.getByText('S-001')).toBeInTheDocument();
    expect(screen.getByText('草稿')).toBeInTheDocument();
    expect(screen.getByText('JPTYO → USNYC（V001）')).toBeInTheDocument();
    expect(screen.getByText('14 日')).toBeInTheDocument();
  });

  it('US01: 候補なしの見積では再検索を促すメッセージが表示される', async () => {
    vi.mocked(quoteApi.fetchQuotation).mockResolvedValue({ ...mockQuotation, candidates: [] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/ルート候補がありません/)).toBeInTheDocument()
    );
  });

  it('US01: 予約化ボタンを押すと予約フォームへ遷移する', async () => {
    vi.mocked(quoteApi.fetchQuotation).mockResolvedValue(mockQuotation);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('見積 Q-001'));
    await user.click(screen.getByRole('button', { name: '予約化' }));

    expect(screen.getByText('新規予約フォーム')).toBeInTheDocument();
  });

  it('H4: 予約化で見積情報が予約フォームへプリセットとして渡される', async () => {
    vi.mocked(quoteApi.fetchQuotation).mockResolvedValue(mockQuotation);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('見積 Q-001'));
    await user.click(screen.getByRole('button', { name: '予約化' }));

    const preset = JSON.parse(screen.getByTestId('preset').textContent ?? '{}');
    expect(preset).toMatchObject({
      shipperId: 'S-001',
      originUnlocode: 'JPTYO',
      destinationUnlocode: 'USNYC',
      arrivalDeadline: '2026-09-30',
      cargoType: 'GENERAL',
      weightKg: 1500,
    });
  });

  it('US01: 取得失敗時にエラーが表示される', async () => {
    vi.mocked(quoteApi.fetchQuotation).mockRejectedValue(new Error('見積が見つかりません'));
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('見積が見つかりません')
    );
  });
});
