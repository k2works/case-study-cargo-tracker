import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminUserListPage } from './AdminUserListPage';
import { useAuthStore } from '@/shared/auth/authStore';

const NOW = new Date('2026-09-03T12:00:00+09:00');

function userRow(over: Record<string, unknown> = {}) {
  return {
    username: 'routing01',
    displayName: '経路 次郎',
    roles: ['ROLE_ROUTING'],
    enabled: true,
    failedAttempts: 5,
    lockedUntil: '2026-09-03T03:12:00Z', // 12:12 JST = あと 12 分
    locked: true,
    ...over,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AdminUserListPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(NOW);
  useAuthStore.setState({ user: { username: 'admin01', roles: ['ROLE_ADMIN'], token: 't' } });
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('S90 利用者管理', () => {
  it('ロック中は残り時間を出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ users: [userRow()] }), { status: 200 }),
    );

    renderPage();

    // 「ロック中」だけだと、待てば入れるのか解除が要るのかが分からない。
    expect(await screen.findByText(/ロック中（あと 12 分）/)).toBeInTheDocument();
  });

  it('ロックされていない利用者には解除ボタンを出さない', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          users: [userRow({ username: 'sales01', locked: false, lockedUntil: null, failedAttempts: 0 })],
        }),
        { status: 200 },
      ),
    );

    renderPage();

    await screen.findByText('sales01');
    // 押しても何も変わらないボタンを出すと、押した人が状態を読み違える。
    expect(screen.queryByRole('button', { name: '解除する' })).not.toBeInTheDocument();
  });

  it('ロックに至っていない失敗回数も出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          users: [userRow({ username: 'handler01', locked: false, lockedUntil: null, failedAttempts: 3 })],
        }),
        { status: 200 },
      ),
    );

    renderPage();

    expect(await screen.findByText('失敗 3 回')).toBeInTheDocument();
  });

  it('解除すると一覧を取り直す', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ users: [userRow()] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValue(
        new Response(
          JSON.stringify({ users: [userRow({ locked: false, lockedUntil: null, failedAttempts: 0 })] }),
          { status: 200 },
        ),
      );

    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: '解除する' }));

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: '解除する' })).not.toBeInTheDocument();
    });
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/admin/users/routing01/unlock');
  });

  it('無効化された利用者は無効と出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          users: [userRow({ username: 'retired01', enabled: false, locked: false, lockedUntil: null })],
        }),
        { status: 200 },
      ),
    );

    renderPage();

    expect(await screen.findByText('無効')).toBeInTheDocument();
  });
});
