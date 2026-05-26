import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as routingApi from '../api/routingApi';
import RouteDesignWorkbenchPage from './RouteDesignWorkbenchPage';

vi.mock('../api/routingApi');

const request = {
  bookingId: 'B-001',
  originUnlocode: 'JPTYO',
  destinationUnlocode: 'DEHAM',
  arrivalDeadline: '2026-08-01',
  cargoType: 'GENERAL',
  status: 'PENDING',
  requestedAt: '2026-06-01T09:00:00',
};

const directCandidate = {
  sequence: 1,
  legs: [
    {
      voyageNumber: 'V-DIRECT',
      loadUnlocode: 'JPTYO',
      unloadUnlocode: 'DEHAM',
      loadTime: '2026-07-03T09:00:00',
      unloadTime: '2026-07-28T18:00:00',
    },
  ],
  voyageNumbers: ['V-DIRECT'],
  ports: ['JPTYO', 'DEHAM'],
  estimatedDays: 25,
  estimatedCost: 1200000,
  currency: 'JPY',
  direct: true,
  recommended: true,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/routing/design/B-001']}>
      <Routes>
        <Route path="/routing/design/:bookingId" element={<RouteDesignWorkbenchPage />} />
        <Route path="/routing/design" element={<div>待ちリスト</div>} />
        <Route path="/bookings/:bookingId" element={<div>予約詳細</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('RouteDesignWorkbenchPage (US08/US09/US11)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(routingApi.fetchRouteDesignRequest).mockResolvedValue(request);
  });

  it('US08: 経路候補を算出して推奨順に表示する', async () => {
    vi.mocked(routingApi.calculateRoutes).mockResolvedValue([directCandidate]);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('経路設計ワークベンチ'));
    await user.click(screen.getByRole('button', { name: '経路候補を算出' }));

    await waitFor(() => expect(screen.getByText('JPTYO → DEHAM')).toBeInTheDocument());
    expect(screen.getByText('経路候補を 1 件算出しました')).toBeInTheDocument();
    expect(screen.getByText('V-DIRECT')).toBeInTheDocument();
  });

  it('US08: 候補なしの場合は条件緩和の警告を表示する', async () => {
    vi.mocked(routingApi.calculateRoutes).mockResolvedValue([]);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('経路設計ワークベンチ'));
    await user.click(screen.getByRole('button', { name: '経路候補を算出' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('期限内に到達可能な経路がありません')
    );
  });

  it('US11: 経路を予約に紐付けると予約詳細へ遷移する', async () => {
    vi.mocked(routingApi.calculateRoutes).mockResolvedValue([directCandidate]);
    vi.mocked(routingApi.confirmRoute).mockResolvedValue(undefined);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('経路設計ワークベンチ'));
    await user.click(screen.getByRole('button', { name: '経路候補を算出' }));
    await waitFor(() => screen.getByText('V-DIRECT'));
    await user.click(screen.getByRole('button', { name: '経路を予約に紐付け' }));

    await waitFor(() => expect(routingApi.confirmRoute).toHaveBeenCalledWith('B-001', 1));
    await waitFor(() => expect(screen.getByText('予約詳細')).toBeInTheDocument());
  });
});
