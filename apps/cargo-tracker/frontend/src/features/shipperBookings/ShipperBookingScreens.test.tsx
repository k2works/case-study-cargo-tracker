import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ShipperBookingListPage } from './ShipperBookingListPage';
import { ShipperBookingProgressPage } from './ShipperBookingProgressPage';
import * as api from './api';

/**
 * S45 自社予約一覧・S46 自社予約の進み具合（引き継ぎ 2）。
 *
 * <p><b>行き先を数え上げる。</b> S46 から出るリンクは 3 本（一覧・追跡・請求書）
 * あり、追跡は追跡番号が出てからしか出ない。1 本ずつ思いついた順に検査すると、
 * 次に足したリンクが黙って漏れる。</p>
 */
function renderWith(ui: React.ReactElement, path = '/shipper/bookings') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/shipper/bookings" element={ui} />
          <Route path="/shipper/bookings/:bookingId" element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const LIST_ITEM = {
  bookingId: 'B-1',
  bookingNumber: 'B-2026-0902-004',
  originUnLocode: 'JPTYO',
  destinationUnLocode: 'USNYC',
  arrivalDeadline: '2026-10-20',
  productName: '自動車部品',
  bookingStatus: 'IN_TRANSIT',
  trackingNumber: 'TRK-8K2QX7M4RB',
};

const PROGRESS = {
  bookingId: 'B-1',
  bookingNumber: 'B-2026-0902-004',
  originUnLocode: 'JPTYO',
  destinationUnLocode: 'USNYC',
  arrivalDeadline: '2026-10-20',
  cargoType: 'GENERAL',
  productName: '自動車部品',
  bookingStatus: 'ROUTE_PROPOSED',
  routingStatus: 'ROUTED',
  bookedAt: '2026-09-02T05:00:00Z',
  routingRequestedAt: '2026-09-02T06:00:00Z',
  lastNotifiedAt: null,
  confirmedAt: null,
  trackingNumber: null,
  trackingIssuedAt: null,
  legs: [],
  notifications: [],
};

describe('S45 自社予約一覧', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('自社の予約が並び、行から S46 へ行ける', async () => {
    vi.spyOn(api, 'fetchShipperBookings').mockResolvedValue({
      state: 'ready',
      value: { items: [LIST_ITEM], total: 1 },
    });

    renderWith(<ShipperBookingListPage />);

    expect(await screen.findByText('B-2026-0902-004')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'B-2026-0902-004' }))
      .toHaveAttribute('href', '/shipper/bookings/B-1');
  });

  it('金額は出さない（金額を出す荷主向けの画面は S62 だけ）', async () => {
    vi.spyOn(api, 'fetchShipperBookings').mockResolvedValue({
      state: 'ready',
      value: { items: [LIST_ITEM], total: 1 },
    });

    renderWith(<ShipperBookingListPage />);

    await screen.findByText('B-2026-0902-004');
    expect(screen.queryByText(/円|¥|JPY/)).not.toBeInTheDocument();
  });

  it('上限で切れていることを黙らない', async () => {
    vi.spyOn(api, 'fetchShipperBookings').mockResolvedValue({
      state: 'ready',
      value: { items: [LIST_ITEM], total: 42 },
    });

    renderWith(<ShipperBookingListPage />);

    expect(await screen.findByText(/42 件のうち 1 件を表示/)).toBeInTheDocument();
  });
});

describe('S46 自社予約の進み具合', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  /** S46 から出る行き先。**数え上げる**——1 本ずつ書くと次に足した先が漏れる。 */
  const DESTINATIONS: ReadonlyArray<readonly [string, string, string | null]> = [
    ['予約一覧へ', '/shipper/bookings', null],
    ['請求書を見る', '/shipper/invoices/by-booking/B-1', null],
    ['追跡を見る', '/tracking/TRK-8K2QX7M4RB', 'TRK-8K2QX7M4RB'],
  ];

  it.each(DESTINATIONS)('「%s」から %s へ行ける', async (label, href, trackingNumber) => {
    vi.spyOn(api, 'fetchShipperBookingProgress').mockResolvedValue({
      state: 'ready',
      value: { ...PROGRESS, trackingNumber },
    });

    renderWith(<ShipperBookingProgressPage />, '/shipper/bookings/B-1');

    expect(await screen.findByRole('link', { name: label })).toHaveAttribute('href', href);
  });

  it('追跡番号が出ていなければ、追跡へのリンクは出さない（行き止まりを作らない）', async () => {
    vi.spyOn(api, 'fetchShipperBookingProgress').mockResolvedValue({
      state: 'ready',
      value: PROGRESS,
    });

    renderWith(<ShipperBookingProgressPage />, '/shipper/bookings/B-1');

    await screen.findByRole('link', { name: '予約一覧へ' });
    expect(screen.queryByRole('link', { name: '追跡を見る' })).not.toBeInTheDocument();
  });

  it('他社の予約は「見つかりません」として扱う（在ることを教えない）', async () => {
    vi.spyOn(api, 'fetchShipperBookingProgress').mockRejectedValue(new Error('404'));

    renderWith(<ShipperBookingProgressPage />, '/shipper/bookings/B-OTHER');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('予約が見つかりません');
    });
  });

  it('進み具合は済んだ段と これからの段を区別して出す', async () => {
    vi.spyOn(api, 'fetchShipperBookingProgress').mockResolvedValue({
      state: 'ready',
      value: PROGRESS,
    });

    renderWith(<ShipperBookingProgressPage />, '/shipper/bookings/B-1');

    expect(await screen.findByText(/仮受付/)).toBeInTheDocument();
    expect(screen.getByText(/予約確定（これから）/)).toBeInTheDocument();
  });
});
