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

/**
 * ダッシュボードは読み口を 3 つ引く。<b>URL で出し分ける。</b>
 *
 * <p>1 つの本体を全部の問い合わせに返すと、本物が返さない形で検査が通る。</p>
 */
function mockApi(
  summary: Record<string, number>,
  conditionReviews: unknown[] = [],
  awaitingConfirmation: unknown[] = [],
  awaitingTracking: unknown[] = [],
) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = String(input);
    if (url.includes('/condition-reviews')) {
      return Promise.resolve(
        new Response(JSON.stringify({ items: conditionReviews }), { status: 200 }));
    }
    if (url.includes('/awaiting-confirmation')) {
      return Promise.resolve(
        new Response(JSON.stringify({ items: awaitingConfirmation }), { status: 200 }));
    }
    if (url.includes('/awaiting-tracking-number')) {
      return Promise.resolve(
        new Response(JSON.stringify({ items: awaitingTracking }), { status: 200 }));
    }
    return Promise.resolve(new Response(JSON.stringify(summary), { status: 200 }));
  });
}

const AWAITING = [
  {
    bookingId: 'b-9',
    bookingNumber: 'B-2026-0903-0009',
    notifiedAt: '2026-09-07T00:00:00Z',
  },
];

beforeEach(() => {
  mockApi({ preliminary: 3, routingWorklist: 5, awaitingNotification: 2 });
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
    mockApi({ preliminary: 0, routingWorklist: 0, awaitingNotification: 0 });

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

  it('US10 §4: 営業には見直しを頼まれた予約が理由つきで出て、そこから行ける', async () => {
    // 打てる手を持つのは営業（荷主と条件を協議する）。件数だけでは、何を協議
    // すればよいのか分からない。
    mockApi({ preliminary: 0, routingWorklist: 0 }, [
      {
        bookingId: 'b-9',
        bookingNumber: 'B-2026-0903-0009',
        reason: '期限内に着ける便がありません',
        requestedAt: '2026-09-06T00:00:00Z',
      },
    ]);

    renderAs(['ROLE_SALES']);

    const row = await screen.findByTestId('condition-review-b-9');
    expect(row).toHaveTextContent('期限内に着ける便がありません');
    expect(within(row).getByRole('link', { name: 'B-2026-0903-0009' }))
      .toHaveAttribute('href', '/bookings/b-9');
  });

  it('US10 §4: 経路設計には見直し依頼を出さない（受け皿は S30）', async () => {
    mockApi({ preliminary: 0, routingWorklist: 0 }, [
      {
        bookingId: 'b-9',
        bookingNumber: 'B-2026-0903-0009',
        reason: '組めません',
        requestedAt: '2026-09-06T00:00:00Z',
      },
    ]);

    renderAs(['ROLE_ROUTING']);
    await screen.findByRole('heading', { name: '今日の作業' });

    expect(screen.queryByText(/条件の見直しを頼まれた予約/)).not.toBeInTheDocument();
  });

  it('US12: 営業には通知していない経路確定済みの予約の件数を出す', async () => {
    renderAs(['ROLE_SALES']);

    const notice = await screen.findByText(/荷主へ通知していない経路確定済みの予約が 2 件/);
    expect(within(notice.closest('output') as HTMLElement)
      .getByRole('link', { name: '予約一覧' })).toHaveAttribute('href', '/bookings');
  });

  it('US13 §3: 営業には確定を待っている予約の行を出し、そこから予約詳細へ行ける', async () => {
    // **件数だけでは仕事が進まない。** 通知したまま確定を忘れた予約は、追跡番号の
    // 発行も輸送手配も始まらない。どの予約を開けばよいかが読めなければならない。
    mockApi({ preliminary: 0, routingWorklist: 0, awaitingNotification: 0 }, [], AWAITING);

    renderAs(['ROLE_SALES']);

    const row = await screen.findByTestId('awaiting-confirmation-b-9');
    expect(within(row).getByRole('link', { name: 'B-2026-0903-0009' }))
      .toHaveAttribute('href', '/bookings/b-9');
  });

  it('US13 §3: 経路設計には確定待ちを出さない（確定は営業の仕事）', async () => {
    mockApi({ preliminary: 0, routingWorklist: 0, awaitingNotification: 0 }, [], AWAITING);

    renderAs(['ROLE_ROUTING']);
    await screen.findByRole('heading', { name: '今日の作業' });

    expect(screen.queryByText(/確定していない予約/)).not.toBeInTheDocument();
  });

  it('US14: 経路設計には発行待ちの予約の行を出し、そこから予約詳細へ行ける', async () => {
    // **確定したまま発行を忘れると、荷主は追跡番号を受け取れない。** US13 §3 の
    // 「経路設計者への通知」は送信基盤がスコープ外なので、この受け皿で代える。
    mockApi({ preliminary: 0, routingWorklist: 0, awaitingNotification: 0 }, [], [], [
      { bookingId: 'b-7', bookingNumber: 'B-2026-0903-0007',
        confirmedAt: '2026-09-08T00:00:00Z' },
    ]);

    renderAs(['ROLE_ROUTING']);

    const row = await screen.findByTestId('awaiting-tracking-b-7');
    expect(within(row).getByRole('link', { name: 'B-2026-0903-0007' }))
      .toHaveAttribute('href', '/bookings/b-7');
  });

  it('US14: 営業には発行待ちを出さない（発行は経路設計者の仕事）', async () => {
    mockApi({ preliminary: 0, routingWorklist: 0, awaitingNotification: 0 }, [], [], [
      { bookingId: 'b-7', bookingNumber: 'B-2026-0903-0007',
        confirmedAt: '2026-09-08T00:00:00Z' },
    ]);

    renderAs(['ROLE_SALES']);
    await screen.findByRole('heading', { name: '今日の作業' });

    expect(screen.queryByText(/追跡番号の発行を待っている/)).not.toBeInTheDocument();
  });

  it('US12: 経路設計には通知していない件数を出さない（通知は営業の仕事）', async () => {
    renderAs(['ROLE_ROUTING']);
    await screen.findByRole('heading', { name: '今日の作業' });

    expect(screen.queryByText(/荷主へ通知していない/)).not.toBeInTheDocument();
  });
});
