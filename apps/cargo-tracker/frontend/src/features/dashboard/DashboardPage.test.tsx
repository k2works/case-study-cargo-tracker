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
    new Response(JSON.stringify({ preliminary: 3, routingWorklist: 5 }), { status: 200 }),
  );
});
afterEach(() => vi.restoreAllMocks());

describe('S02 ダッシュボード', () => {
  it('営業には引き渡していない予約の件数と、そこから行ける導線を出す', async () => {
    // US04 §受入基準 5 の通知は送信基盤がスコープ外。担当者はここで気づく。
    // 引き渡すのは営業の仕事（US06）なので、件数は営業に出す。
    renderAs(['ROLE_SALES']);

    const notice = await screen.findByText(/経路設計者へ引き渡していない予約が 3 件/);
    // 知らせの中から直接行けること。「今日の作業」の一覧にもリンクはあるが、
    // 件数を読んだその場から行けなければ、気づきが次の行動に繋がらない。
    const link = within(notice.closest('output') as HTMLElement)
      .getByRole('link', { name: '予約一覧' });
    expect(link).toHaveAttribute('href', '/bookings');
  });

  it('経路設計には設計を待っている件数と、作業一覧への導線を出す', async () => {
    // 経路設計者に「引き渡していない予約」を出しても、その件数に対して
    // 打てる手が無い（引き渡すのは営業）。自分の作業の件数を出す。
    renderAs(['ROLE_ROUTING']);

    const notice = await screen.findByText(/経路設計を待っている予約が 5 件/);
    const link = within(notice.closest('output') as HTMLElement)
      .getByRole('link', { name: '経路設計作業一覧' });
    expect(link).toHaveAttribute('href', '/routing/worklist');
  });

  it('経路設計に、引き渡していない予約の件数は出さない', async () => {
    renderAs(['ROLE_ROUTING']);
    await screen.findByRole('heading', { name: '今日の作業' });

    expect(screen.queryByText(/引き渡していない予約が/)).not.toBeInTheDocument();
  });

  it('0 件のときは知らせない', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ preliminary: 0, routingWorklist: 0 }), { status: 200 }),
    );

    renderAs(['ROLE_ROUTING', 'ROLE_SALES']);
    await screen.findByRole('heading', { name: '今日の作業' });

    // 0 件を強調すると、毎朝「0 件」を読み飛ばす習慣がついて、件数が出た日も見落とす。
    expect(screen.queryByText(/件あります/)).not.toBeInTheDocument();
  });

  it('どちらのロールでもない利用者には件数を出さない', async () => {
    renderAs(['ROLE_TRACKER']);
    await screen.findByRole('heading', { name: '今日の作業' });

    expect(screen.queryByText(/件あります/)).not.toBeInTheDocument();
  });
});
