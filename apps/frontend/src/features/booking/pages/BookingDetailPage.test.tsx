import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as bookingApi from '../api/bookingApi';
import BookingDetailPage from './BookingDetailPage';

vi.mock('../api/bookingApi');

const baseBooking = {
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
  routeNotifiedAt: null,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/bookings/B-001']}>
      <Routes>
        <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
        <Route path="/bookings" element={<div>予約一覧</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('BookingDetailPage (US06/US13)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US06/US13: 予約詳細が表示される', async () => {
    vi.mocked(bookingApi.fetchBooking).mockResolvedValue(baseBooking);
    renderPage();

    await waitFor(() => expect(screen.getByText('予約 B-001')).toBeInTheDocument());
    expect(screen.getByText('S-001')).toBeInTheDocument();
    expect(screen.getByText('JPTYO')).toBeInTheDocument();
  });

  it('US06: 仮受付の予約で経路設計依頼が実行され再取得される', async () => {
    vi.mocked(bookingApi.fetchBooking)
      .mockResolvedValueOnce(baseBooking)
      .mockResolvedValueOnce({ ...baseBooking, bookingStatus: 'ROUTING' });
    vi.mocked(bookingApi.handoffBooking).mockResolvedValue(undefined);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('予約 B-001'));
    await user.click(screen.getByRole('button', { name: '経路設計を依頼' }));

    await waitFor(() => expect(bookingApi.handoffBooking).toHaveBeenCalledWith('B-001'));
  });

  it('US13: 確定済みの予約はキャンセルボタンが無効になる', async () => {
    vi.mocked(bookingApi.fetchBooking).mockResolvedValue({ ...baseBooking, bookingStatus: 'CONFIRMED' });
    renderPage();

    await waitFor(() => screen.getByText('予約 B-001'));
    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '経路設計を依頼' })).toBeDisabled();
  });

  it('US06/US13: 取得失敗時にエラーが表示される', async () => {
    vi.mocked(bookingApi.fetchBooking).mockRejectedValue(new Error('予約が見つかりません'));
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('予約が見つかりません')
    );
  });

  it('US11/US12: 経路提案中で確定経路が表示され荷主通知ボタンが有効になる', async () => {
    vi.mocked(bookingApi.fetchBooking).mockResolvedValue({
      ...baseBooking,
      bookingStatus: 'ROUTE_PROPOSED',
      routingStatus: 'ROUTED',
    });
    vi.mocked(bookingApi.fetchRoute).mockResolvedValue([
      {
        legSeq: 1,
        voyageNumber: 'V-A',
        loadUnlocode: 'JPTYO',
        unloadUnlocode: 'DEHAM',
        loadAt: '2026-07-03T09:00:00',
        unloadAt: '2026-07-28T18:00:00',
      },
    ]);
    renderPage();

    await waitFor(() => screen.getByText('予約 B-001'));
    await waitFor(() => expect(screen.getByText('確定経路')).toBeInTheDocument());
    expect(screen.getByText('経由港: JPTYO → DEHAM')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '荷主に経路を通知' })).toBeEnabled();
  });

  it('US12: 荷主通知ボタンで notifyRoute が呼ばれる', async () => {
    vi.mocked(bookingApi.fetchBooking).mockResolvedValue({
      ...baseBooking,
      bookingStatus: 'ROUTE_PROPOSED',
      routingStatus: 'ROUTED',
    });
    vi.mocked(bookingApi.fetchRoute).mockResolvedValue([]);
    vi.mocked(bookingApi.notifyRoute).mockResolvedValue(undefined);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('予約 B-001'));
    await user.click(screen.getByRole('button', { name: '荷主に経路を通知' }));

    await waitFor(() => expect(bookingApi.notifyRoute).toHaveBeenCalledWith('B-001'));
  });
});
