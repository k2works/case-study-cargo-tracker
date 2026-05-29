import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as trackingApi from '../api/trackingApi';
import TrackingListPage from './TrackingListPage';

vi.mock('../api/trackingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/trackingApi')>(
    '../api/trackingApi',
  );
  return {
    ...actual,
    fetchTrackingPage: vi.fn(),
  };
});

const sampleSummary = {
  trackingNumber: 'TRK-AB12CD3456',
  bookingId: 'B-001',
  currentStatus: 'IN_TRANSIT' as const,
  currentUnlocode: 'JPTYO',
  currentVoyageNumber: 'V-MAERSK-220',
  estimatedArrival: null,
  misrouted: false,
  lastEventAt: '2026-07-21T18:00:00',
  deliveredAt: null,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/tracking']}>
      <Routes>
        <Route path="/tracking" element={<TrackingListPage />} />
        <Route path="/tracking/:trackingNumber/manage" element={<div>状態管理画面</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('TrackingListPage', () => {
  it('US17: tracking_summary の一覧を表示する', async () => {
    vi.mocked(trackingApi.fetchTrackingPage).mockResolvedValueOnce({
      items: [sampleSummary],
      totalCount: 1,
      page: 0,
      size: 20,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });
    expect(screen.getByText('B-001')).toBeInTheDocument();
    expect(screen.getByText('輸送中')).toBeInTheDocument();
    expect(screen.getByText('JPTYO')).toBeInTheDocument();
  });

  it('US17: 誤配送の場合は赤色バッジを表示する', async () => {
    vi.mocked(trackingApi.fetchTrackingPage).mockResolvedValueOnce({
      items: [{ ...sampleSummary, misrouted: true, currentStatus: 'MISROUTED' }],
      totalCount: 1,
      page: 0,
      size: 20,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('誤配送')).toBeInTheDocument();
    });
  });

  it('US17: 追跡番号クリックで状態管理画面へ遷移する', async () => {
    vi.mocked(trackingApi.fetchTrackingPage).mockResolvedValueOnce({
      items: [sampleSummary],
      totalCount: 1,
      page: 0,
      size: 20,
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText('TRK-AB12CD3456'));
    expect(screen.getByText('状態管理画面')).toBeInTheDocument();
  });

  it('US17: 一覧が空ならガイダンスを表示する', async () => {
    vi.mocked(trackingApi.fetchTrackingPage).mockResolvedValueOnce({
      items: [],
      totalCount: 0,
      page: 0,
      size: 20,
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('追跡対象の貨物がありません')).toBeInTheDocument();
    });
  });
});
