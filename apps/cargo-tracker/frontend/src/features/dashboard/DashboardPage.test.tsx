import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';
import { useAuthStore } from '@/shared/auth/authStore';
import type { Role } from '@/shared/auth/roles';

function renderAs(roles: readonly Role[]) {
  useAuthStore.setState({ user: { username: 'u', roles, token: 't' } });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ preliminary: 3 }), { status: 200 }),
  );
});
afterEach(() => vi.restoreAllMocks());

describe('S02 ダッシュボード', () => {
  it('経路設計には引き渡し待ちの件数と、そこから行ける導線を出す', async () => {
    // US04 §受入基準 5 の通知は送信基盤がスコープ外。経路設計者はここで気づく。
    // 件数を出すだけでは仕事が進まないので、対象へ行けることまで確かめる。
    renderAs(['ROLE_ROUTING']);

    const notice = await screen.findByText(/引き渡し待ちの予約が 3 件/);
    // 知らせの中から直接行けること。「今日の作業」の一覧にもリンクはあるが、
    // 件数を読んだその場から行けなければ、気づきが次の行動に繋がらない。
    const link = within(notice.closest('output') as HTMLElement)
      .getByRole('link', { name: '予約一覧' });
    expect(link).toHaveAttribute('href', '/bookings');
  });

  it('0 件のときは知らせない', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ preliminary: 0 }), { status: 200 }),
    );

    renderAs(['ROLE_ROUTING']);
    await screen.findByRole('heading', { name: '今日の作業' });

    // 0 件を強調すると、毎朝「0 件」を読み飛ばす習慣がついて、件数が出た日も見落とす。
    expect(screen.queryByText(/引き渡し待ちの予約が/)).not.toBeInTheDocument();
  });

  it('経路設計以外には出さない', async () => {
    renderAs(['ROLE_SALES']);
    await screen.findByRole('heading', { name: '今日の作業' });

    expect(screen.queryByText(/引き渡し待ちの予約が/)).not.toBeInTheDocument();
  });
});
