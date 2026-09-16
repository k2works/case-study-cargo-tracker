import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DeadLetterListPage } from './DeadLetterListPage';
import { useAuthStore } from '@/shared/auth/authStore';

function entry(over: Record<string, unknown> = {}) {
  return {
    deadLetterId: 'dl-1',
    processingGroup: 'com.example.cargotracker.booking.infrastructure.projection',
    sequenceIdentifier: 'B-2026-0902-004',
    eventType: 'com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent',
    eventIdentifier: 'evt-1',
    enqueuedAt: '2026-09-25T01:20:00Z',
    causeType: 'org.springframework.dao.DataIntegrityViolationException',
    causeMessage: '値が長すぎます（identifier）',
    ...over,
  };
}

/** 5 サービスぶんの応答。順は `SOURCES` と同じ（Promise.all）。 */
function respondWith(...perService: unknown[][]) {
  let call = 0;
  return vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
    const items = perService[call] ?? [];
    call += 1;
    return Promise.resolve(new Response(JSON.stringify({ items }), { status: 200 }));
  });
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DeadLetterListPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'admin01', roles: ['ROLE_ADMIN'], token: 't' } });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('S91 退避したイベント', () => {
  it('止まった理由が出る（次の行動を決めるのはこれ）', async () => {
    respondWith([entry()], [], [], [], []);

    renderPage();

    // **件数だけでは何もできない。** どの処理が・なぜ止まったかが要る。
    expect(await screen.findByText('値が長すぎます（identifier）')).toBeInTheDocument();
    expect(screen.getByText('B-2026-0902-004')).toBeInTheDocument();
  });

  it('5 つのサービスを束ねて新しい順に出す', async () => {
    respondWith(
      [entry({ deadLetterId: 'dl-booking', enqueuedAt: '2026-09-25T01:20:00Z' })],
      [],
      [entry({ deadLetterId: 'dl-tracking', enqueuedAt: '2026-09-25T02:00:00Z',
        causeMessage: 'null が入りません（unlocode）' })],
      [],
      [],
    );

    renderPage();

    const rows = await screen.findAllByRole('row');
    // 見出し行のつぎが最新。**古い順に出すと、いま起きていることが下に沈む。**
    expect(rows[1]).toHaveTextContent('null が入りません（unlocode）');
    expect(rows[2]).toHaveTextContent('値が長すぎます（identifier）');
  });

  it('退避が無ければ、そう書く（空の表を出さない）', async () => {
    respondWith([], [], [], [], []);

    renderPage();

    expect(await screen.findByText('退避しているイベントはありません。')).toBeInTheDocument();
    // 処理し直す先が無いのにボタンを出すと、押した人が「効かない」と受け取る。
    expect(screen.queryByRole('button', { name: /処理し直す/ })).not.toBeInTheDocument();
  });

  it('処理し直す宛先は退避のあるサービスだけ', async () => {
    const fetchMock = respondWith([entry()], [], [], [], []);

    renderPage();
    await screen.findByText('値が長すぎます（identifier）');

    // **止まっていないサービスまで回さない。**
    expect(screen.getAllByRole('button', { name: /処理し直す/ })).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'booking を処理し直す' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/booking/dead-letters/retry'),
        expect.objectContaining({ method: 'POST' }),
      );
    });
  });

  it('消す口は無い（直したあとに退避を消すのは黙って捨てること）', async () => {
    respondWith([entry()], [], [], [], []);

    renderPage();
    await screen.findByText('値が長すぎます（identifier）');

    // ADR-0014 決定 1。**捨てる手段を画面に置かない。**
    expect(screen.queryByRole('button', { name: /削除|消す|捨てる/ })).not.toBeInTheDocument();
  });
});
