import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TrackingListPage } from './TrackingListPage';
import { useAuthStore } from '@/shared/auth/authStore';

function item(over: Record<string, unknown> = {}) {
  return {
    trackingNumber: 'TRK-8K2QX7M4RB',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    statusLabel: '輸送中',
    currentUnLocode: 'SGSIN',
    estimatedArrival: '2026-09-24T18:00:00Z',
    lastStatusChangedAt: '2026-09-11T02:00:00Z',
    ...over,
  };
}

function respondWith(body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body),
  } as Response);
}

function renderList() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/tracking']}>
        <Routes>
          <Route path="/tracking" element={<TrackingListPage />} />
          <Route path="/tracking/:trackingNumber" element={<h1>追跡詳細</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S40 追跡一覧', () => {
  it('追跡が行で出て、詳細へ行ける', async () => {
    respondWith({ items: [item()] });

    renderList();

    expect(await screen.findByRole('link', { name: 'TRK-8K2QX7M4RB' }))
      .toHaveAttribute('href', '/tracking/TRK-8K2QX7M4RB');
    expect(screen.getByText('輸送中')).toBeInTheDocument();
    expect(screen.getByText('SGSIN')).toBeInTheDocument();
  });

  it('既定では引取済を含めない（いま追うものだけが並ぶ）', async () => {
    const fetchSpy = respondWith({ items: [] });

    renderList();

    await screen.findByText(/追跡はありません/);
    expect(fetchSpy.mock.calls[0]?.[0]).toContain('includeDelivered=false');
  });

  it('「引取済も表示」で含められる', async () => {
    const fetchSpy = respondWith({ items: [] });

    renderList();
    await screen.findByText(/追跡はありません/);
    await userEvent.click(screen.getByLabelText('引取済も表示'));

    await vi.waitFor(() =>
      expect(fetchSpy.mock.calls.at(-1)?.[0]).toContain('includeDelivered=true'));
  });

  it('1 件も無いときは、次に何が起きれば出るのかを書く', async () => {
    // 空欄だけだと、利用者は壊れているのか自分の担当が無いのか判断できない。
    respondWith({ items: [] });

    renderList();

    expect(await screen.findByText(/追跡番号が発行されると/)).toBeInTheDocument();
  });
});
