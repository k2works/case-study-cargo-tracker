import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as routingApi from '../api/routingApi';
import RouteDesignListPage from './RouteDesignListPage';

vi.mock('../api/routingApi');

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/routing/design']}>
      <Routes>
        <Route path="/routing/design" element={<RouteDesignListPage />} />
        <Route path="/routing/design/:bookingId" element={<div>ワークベンチ</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('RouteDesignListPage (US08)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US08: 経路設計待ちリストを表示する', async () => {
    vi.mocked(routingApi.fetchRouteDesignRequests).mockResolvedValue([
      {
        bookingId: 'B-001',
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'DEHAM',
        arrivalDeadline: '2026-08-01',
        cargoType: 'GENERAL',
        status: 'PENDING',
        requestedAt: '2026-06-01T09:00:00',
      },
    ]);
    renderPage();

    await waitFor(() => expect(screen.getByText('B-001')).toBeInTheDocument());
    expect(screen.getByText('DEHAM')).toBeInTheDocument();
  });

  it('US08: 待ちリストが空の場合はメッセージを表示する', async () => {
    vi.mocked(routingApi.fetchRouteDesignRequests).mockResolvedValue([]);
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('経路設計待ちの予約はありません。')).toBeInTheDocument()
    );
  });

  it('US08: 行の経路設計ボタンでワークベンチへ遷移する', async () => {
    vi.mocked(routingApi.fetchRouteDesignRequests).mockResolvedValue([
      {
        bookingId: 'B-001',
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'DEHAM',
        arrivalDeadline: '2026-08-01',
        cargoType: 'GENERAL',
        status: 'PENDING',
        requestedAt: '2026-06-01T09:00:00',
      },
    ]);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('B-001'));
    await user.click(screen.getByRole('button', { name: '経路を設計' }));

    await waitFor(() => expect(screen.getByText('ワークベンチ')).toBeInTheDocument());
  });
});
