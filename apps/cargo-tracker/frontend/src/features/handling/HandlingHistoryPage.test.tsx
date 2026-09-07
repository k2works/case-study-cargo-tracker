import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { HandlingHistoryPage } from './HandlingHistoryPage';
import { useAuthStore } from '@/shared/auth/authStore';

function item(over: Record<string, unknown> = {}) {
  return {
    activityId: 'act-1',
    handlingType: 'UNLOAD',
    handlingTypeLabel: '荷降し',
    unLocode: 'SGSIN',
    voyageNumber: 'V-MOL-001',
    offRoute: false,
    operator: 'handler01',
    completedAt: '2026-09-16T08:30:00Z',
    voided: false,
    voidReason: null,
    ...over,
  };
}

function respondWith(body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify(body), { status: 200 }));
}

function renderHistory() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/handling/TRK-8K2QX7M4RB']}>
        <Routes>
          <Route path="/handling/:trackingNumber" element={<HandlingHistoryPage />} />
          <Route path="/tracking/:trackingNumber" element={<h1>追跡詳細</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('S51 荷役履歴', () => {
  it('荷役が起きた順に出る', async () => {
    useAuthStore.setState({
      user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' },
    });
    respondWith({ trackingNumber: 'TRK-8K2QX7M4RB', items: [item()] });

    renderHistory();

    const row = await screen.findByRole('row', { name: /荷降し/ });
    expect(row).toHaveTextContent('SGSIN');
    expect(row).toHaveTextContent('V-MOL-001');
    expect(row).toHaveTextContent('記録済');
  });

  it('予定外の記録が分かる', async () => {
    useAuthStore.setState({
      user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' },
    });
    respondWith({ trackingNumber: 'TRK-8K2QX7M4RB', items: [item({ offRoute: true })] });

    renderHistory();

    expect(await screen.findByRole('row', { name: /予定外/ })).toBeInTheDocument();
  });

  it('取り消した記録も消えず、理由が読める', async () => {
    // 消えていると、現場で何が起きたのかを後から突き合わせられない。
    useAuthStore.setState({
      user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' },
    });
    respondWith({
      trackingNumber: 'TRK-8K2QX7M4RB',
      items: [item({ voided: true, voidReason: '取り違えました' })],
    });

    renderHistory();

    expect(await screen.findByRole('row', { name: /取消/ })).toHaveTextContent('取り違えました');
  });

  it('荷役ロールには追跡詳細へのリンクを出さない（403 になる）', async () => {
    useAuthStore.setState({
      user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' },
    });
    respondWith({ trackingNumber: 'TRK-8K2QX7M4RB', items: [item()] });

    renderHistory();

    await screen.findByRole('row', { name: /荷降し/ });
    expect(screen.queryByRole('link', { name: '追跡を見る' })).not.toBeInTheDocument();
  });

  it('追跡管理者には追跡詳細へのリンクを出す', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    respondWith({ trackingNumber: 'TRK-8K2QX7M4RB', items: [item()] });

    renderHistory();

    expect(await screen.findByRole('link', { name: '追跡を見る' }))
      .toHaveAttribute('href', '/tracking/TRK-8K2QX7M4RB');
  });

  it('1 件も無いときは、次に何が起きれば出るのかを書く', async () => {
    useAuthStore.setState({
      user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' },
    });
    respondWith({ trackingNumber: 'TRK-8K2QX7M4RB', items: [] });

    renderHistory();

    expect(await screen.findByText(/港で作業が記録されると/)).toBeInTheDocument();
  });
});
