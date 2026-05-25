import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as bookingApi from '../api/bookingApi';
import BookingListPage from './BookingListPage';

vi.mock('../api/bookingApi');

const mockBookings = [
  {
    bookingId: 'B-001',
    shipperId: 'S-001',
    trackingNumber: null,
    originUnlocode: 'JPTYO',
    destinationUnlocode: 'USNYC',
    arrivalDeadline: '2026-09-30',
    cargoType: 'GENERAL',
    weightKg: 1500,
    lengthCm: 120,
    widthCm: 80,
    heightCm: 60,
    quantity: 10,
    productName: '電子部品',
    hazardImoClass: null,
    hazardUnNumber: null,
    hazardDeclaration: null,
    temperatureMinC: null,
    temperatureMaxC: null,
    bookingStatus: 'PRELIMINARY',
    routingStatus: 'NOT_ROUTED',
    estimatedAmount: null,
    estimatedCurrency: null,
  },
];

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/bookings']}>
      <Routes>
        <Route path="/bookings" element={<BookingListPage />} />
        <Route path="/bookings/new" element={<div>新規予約フォーム</div>} />
      </Routes>
    </MemoryRouter>
  );
}

function pageOf(items: typeof mockBookings, totalCount = items.length) {
  return { items, totalCount, page: 0, size: 20 };
}

describe('BookingListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US04: 予約一覧が表示される', async () => {
    vi.mocked(bookingApi.fetchBookingsPage).mockResolvedValue(pageOf(mockBookings));
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('B-001')).toBeInTheDocument()
    );
    expect(screen.getByText('電子部品')).toBeInTheDocument();
    expect(screen.getByText('JPTYO')).toBeInTheDocument();
    expect(screen.getByText('USNYC')).toBeInTheDocument();
    expect(screen.getByText('仮受付')).toBeInTheDocument();
  });

  it('US04: 新規登録ボタンを押すと予約フォームへ遷移する', async () => {
    vi.mocked(bookingApi.fetchBookingsPage).mockResolvedValue(pageOf([]));
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '新規登録' }));

    expect(screen.getByText('新規予約フォーム')).toBeInTheDocument();
  });

  it('US04: 予約が 0 件の場合にメッセージが表示される', async () => {
    vi.mocked(bookingApi.fetchBookingsPage).mockResolvedValue(pageOf([]));
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('登録されている予約はありません')).toBeInTheDocument()
    );
  });

  it('US04: API 取得失敗時にエラーが表示される', async () => {
    vi.mocked(bookingApi.fetchBookingsPage).mockRejectedValue(new Error('API ダウン'));
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('API ダウン')
    );
  });

  it('IT2 ページネーション: 「次へ」クリックで page=1 が API に渡される', async () => {
    const firstPage = { items: mockBookings, totalCount: 47, page: 0, size: 20 };
    const secondPage = { items: [], totalCount: 47, page: 1, size: 20 };
    vi.mocked(bookingApi.fetchBookingsPage)
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce(secondPage);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('B-001'));
    await user.click(screen.getByRole('button', { name: '次へ' }));

    await waitFor(() =>
      expect(bookingApi.fetchBookingsPage).toHaveBeenCalledWith(1, 20)
    );
  });
});
