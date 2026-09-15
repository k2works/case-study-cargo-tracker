import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/shared/auth/authStore';
import type { Role } from '@/shared/auth/roles';
import { ShipperNotice } from './ShipperNotice';

/**
 * 貨物の知らせ（US37 §受入基準 1・2・3・5）。
 *
 * <p><b>行き先を数え上げる</b>（Try T2）。1 件だけ確かめる形は、次に行き先を
 * 足したときも同じ抜け方をする。</p>
 */
function respond(handler: (url: string, init?: RequestInit) => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) =>
    Promise.resolve(handler(String(input), init as RequestInit)),
  );
}

function loginAs(roles: readonly Role[]) {
  useAuthStore.setState({ user: { username: 'tester', roles, token: 't' } });
}

function renderNotice() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShipperNotice />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const NOTICE = {
  sequenceNo: 7,
  trackingNumber: 'TRK-8K2QX7M4RB',
  statusLabel: '積込済',
  location: 'JPTYO',
  occurredAt: '2026-09-15T01:00:00Z',
  originUnLocode: 'JPTYO',
  destinationUnLocode: 'USNYC',
};

describe('貨物の知らせ（ポップアップ）', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAuthStore.setState({ user: null });
  });

  it('US37 §1: 新しい知らせがあると出る', async () => {
    loginAs(['ROLE_SHIPPER']);
    respond(() => new Response(
      JSON.stringify({ items: [NOTICE], latestSequence: 7 }), { status: 200 },
    ));

    renderNotice();

    expect(await screen.findByRole('complementary', { name: '貨物の知らせ' }))
      .toBeInTheDocument();
    expect(screen.getByText('積込済（JPTYO）')).toBeInTheDocument();
  });

  it('US37 §1: 新しい知らせが無ければ出ない', async () => {
    loginAs(['ROLE_SHIPPER']);
    respond(() => new Response(
      JSON.stringify({ items: [], latestSequence: 0 }), { status: 200 },
    ));

    renderNotice();

    await waitFor(() =>
      expect(screen.queryByRole('complementary', { name: '貨物の知らせ' }))
        .not.toBeInTheDocument());
  });

  /** ポップアップから出る行き先。**数え上げる**——次に足した先が漏れない。 */
  it.each([
    ['TRK-8K2QX7M4RB', '/tracking/TRK-8K2QX7M4RB'],
    ['自社の予約一覧へ', '/shipper/bookings'],
  ])('US37 §2: 「%s」から %s へ行ける', async (label, href) => {
    loginAs(['ROLE_SHIPPER']);
    respond(() => new Response(
      JSON.stringify({ items: [NOTICE], latestSequence: 7 }), { status: 200 },
    ));

    renderNotice();

    expect(await screen.findByRole('link', { name: label })).toHaveAttribute('href', href);
  });

  it('US37 §3: 閉じると、サーバが返した位置を既読として送る', async () => {
    loginAs(['ROLE_SHIPPER']);
    const bodies: string[] = [];
    respond((_url, init) => {
      if (init?.method === 'POST') {
        bodies.push(String(init.body));
        return new Response(null, { status: 204 });
      }
      return new Response(
        JSON.stringify({ items: [NOTICE], latestSequence: 12 }), { status: 200 },
      );
    });

    renderNotice();
    await screen.findByRole('complementary', { name: '貨物の知らせ' });
    await userEvent.click(screen.getByRole('button', { name: '閉じる' }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    // **画面が最大値を数えない。** 上限で切れたときに「出していない知らせまで
    // 既読」にしてしまう。
    expect(bodies[0]).toContain('12');
  });

  /**
   * 荷主以外は<b>問い合わせにも行かない</b>（§5）。
   *
   * <p>「返ってきたものが空だから出さない」にすると、他のロールの画面から
   * 403 が出続けて記録が荒れる。<b>ロールを数え上げる</b>。</p>
   */
  it.each([
    ['ROLE_SALES'], ['ROLE_ROUTING'], ['ROLE_TRACKER'],
    ['ROLE_HANDLER'], ['ROLE_ACCOUNTANT'], ['ROLE_ADMIN'],
  ])('US37 §5: %s では何も出ず、問い合わせもしない', async (role) => {
    loginAs([role as Role]);
    const fetchSpy = respond(() => new Response(
      JSON.stringify({ items: [NOTICE], latestSequence: 7 }), { status: 200 },
    ));

    renderNotice();

    await waitFor(() =>
      expect(screen.queryByRole('complementary', { name: '貨物の知らせ' }))
        .not.toBeInTheDocument());
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
