import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CancellationWorklistPage } from './CancellationWorklistPage';
import { BookingCancellationPanel } from './BookingCancellationPanel';
import { useAuthStore } from '@/shared/auth/authStore';

function request(over: Record<string, unknown> = {}) {
  return {
    requestId: 'CR-1',
    bookingId: 'b-1',
    bookingNumber: 'B-2026-0902-0004',
    productName: '止める貨物',
    reason: '荷主の発注取消',
    requestedBy: 'sales01',
    requestedAt: '2026-09-25T01:20:00Z',
    decision: null,
    decisionLabel: '承認待ち',
    dischargeUnLocode: null,
    decisionReason: null,
    decidedBy: null,
    decidedAt: null,
    ...over,
  };
}

/**
 * 承認待ちと陸揚げ地の選択肢を URL で出し分ける。
 *
 * <p>`candidateStatus` を渡すと、選択肢の取得だけを失敗させられる。</p>
 */
function mockApi(items: unknown[], ports: string[] = ['JPTYO', 'SGSIN', 'USNYC'],
  candidateStatus = 200) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = String(input);
    if (url.includes('/discharge-candidates')) {
      if (candidateStatus !== 200) {
        return Promise.resolve(new Response(JSON.stringify({ message: '読めません' }),
          { status: candidateStatus }));
      }
      return Promise.resolve(new Response(JSON.stringify({
        currentUnLocode: 'JPTYO', unLocodes: ports,
      }), { status: 200 }));
    }
    return Promise.resolve(new Response(JSON.stringify({ items }), { status: 200 }));
  });
}

function renderWorklist() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <CancellationWorklistPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderPanel(bookingStatus: string, canRequest = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <BookingCancellationPanel
          bookingId="b-1"
          bookingStatus={bookingStatus}
          canRequest={canRequest}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({
    user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
  });
});
afterEach(() => vi.restoreAllMocks());

describe('S23 キャンセル承認一覧', () => {
  it('US30 §4: 申請が予約の呼び名つきで出る（ID だけでは何の話か分からない）', async () => {
    mockApi([request()]);

    renderWorklist();

    expect(await screen.findByText('B-2026-0902-0004')).toBeInTheDocument();
    expect(screen.getByText('止める貨物')).toBeInTheDocument();
    expect(screen.getByText('荷主の発注取消')).toBeInTheDocument();
  });

  it('US30 §5: 陸揚げ地はサーバの候補から選ぶ（画面で組み立てない）', async () => {
    // **集約が断る条件と同じ関数から作られる。** 画面が組み立てると、出ているのに
    // 押すと断られる港が生まれる。
    mockApi([request()]);

    renderWorklist();
    await userEvent.click(await screen.findByRole('button', { name: '判断する' }));

    const select = await screen.findByLabelText('陸揚げ地');
    expect(select).toHaveTextContent('JPTYO（現在地）');
    expect(select).toHaveTextContent('SGSIN');
  });

  it('US30 §5: 陸揚げ地を選ぶまで承認できない（決めずに承認しない）', async () => {
    mockApi([request()]);

    renderWorklist();
    await userEvent.click(await screen.findByRole('button', { name: '判断する' }));

    // 決めずに承認しても、貨物は船の上に残る。
    expect(await screen.findByRole('button', { name: '承認する' })).toBeDisabled();
  });

  it('US30 §7: 却下には理由が要る（申請した営業が次の手を決められない）', async () => {
    mockApi([request()]);

    renderWorklist();
    await userEvent.click(await screen.findByRole('button', { name: '判断する' }));

    expect(await screen.findByRole('button', { name: '却下する' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('理由'), '荷受人がすでに手配済み');
    expect(screen.getByRole('button', { name: '却下する' })).toBeEnabled();
  });

  it('承認するとサーバへ陸揚げ地が送られる', async () => {
    const fetchSpy = mockApi([request()]);

    renderWorklist();
    await userEvent.click(await screen.findByRole('button', { name: '判断する' }));
    await userEvent.selectOptions(await screen.findByLabelText('陸揚げ地'), 'SGSIN');
    await userEvent.click(screen.getByRole('button', { name: '承認する' }));

    await waitFor(() => {
      const call = fetchSpy.mock.calls.find(
        ([url]) => String(url).includes('/cancellation/approval'));
      expect(call).toBeDefined();
      expect(String(call?.[1]?.body)).toContain('SGSIN');
    });
  });

  it('承認待ちが無ければ、そう書く（空の表を出さない）', async () => {
    mockApi([]);

    renderWorklist();

    expect(await screen.findByText('承認待ちのキャンセル申請はありません。'))
      .toBeInTheDocument();
  });

  it('US30 §5: 陸揚げ地の選択肢が読めなければ、理由が出る', async () => {
    // **押せないまま理由が読めない状態を作らない。** 取得に失敗すると選択肢が
    // 空になり `[承認する]` が押せなくなるが、なぜ押せないのかが画面に無かった
    // （IT15 のレビュー 中）。一覧側は出し分けているのに、判断欄だけ落ちていた。
    mockApi([request()], [], 500);

    renderWorklist();
    await userEvent.click(await screen.findByRole('button', { name: '判断する' }));

    expect(await screen.findByText(/陸揚げ地の選択肢を取得できませんでした/))
      .toBeVisible();
  });
});

describe('S22 キャンセル欄', () => {
  it('US30 §2: 輸送中は「キャンセル（要承認）」になる', async () => {
    // **入口は 1 つで、どちらになるかは集約が決める。** 画面は文言だけ変える。
    mockApi([]);

    renderPanel('IN_TRANSIT');

    expect(await screen.findByRole('button', { name: 'キャンセル（要承認）' }))
      .toBeInTheDocument();
    // **文字列は要素で分かれる**（`<b>` を挟んでいる）。段落ごと見る。
    expect(screen.getByText(/輸送中の予約は/).closest('p'))
      .toHaveTextContent('追跡管理者の承認');
  });

  it('US30 §1: 輸送開始前は「キャンセルする」（申請を挟まない）', async () => {
    mockApi([]);

    renderPanel('TRACKING_ISSUED');

    expect(await screen.findByRole('button', { name: 'キャンセルする' }))
      .toBeInTheDocument();
  });

  it('US30 §3: 理由を書くまで押せない', async () => {
    mockApi([]);

    renderPanel('IN_TRANSIT');

    const button = await screen.findByRole('button', { name: 'キャンセル（要承認）' });
    expect(button).toBeDisabled();
    await userEvent.type(screen.getByLabelText('理由'), '荷主の発注取消');
    expect(button).toBeEnabled();
  });

  it('US30 §8: 配送完了以降は操作を出さない（押せるのに断られる操作を並べない）', async () => {
    mockApi([]);

    const { container } = renderPanel('DELIVERED');

    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });

  it('不変条件 10: 承認待ちのあいだは新しい申請を出せない', async () => {
    mockApi([request()]);

    renderPanel('IN_TRANSIT');

    expect(await screen.findByText(/承認待ちの申請があります/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'キャンセル（要承認）' }))
      .not.toBeInTheDocument();
  });

  it('US30 §10: 誰が・いつ・なぜが揃って履歴になる', async () => {
    mockApi([request({
      decision: 'APPROVED',
      decisionLabel: '承認済',
      dischargeUnLocode: 'SGSIN',
      decisionReason: '荷主の指定倉庫が近い',
      decidedBy: 'tracker01',
      decidedAt: '2026-09-25T02:00:00Z',
    })]);

    renderPanel('CANCELLED', false);

    const row = await screen.findByTestId('cancellation-history-CR-1');
    expect(row).toHaveTextContent('sales01');
    expect(row).toHaveTextContent('荷主の発注取消');
    expect(row).toHaveTextContent('承認済');
    expect(row).toHaveTextContent('tracker01');
    expect(row).toHaveTextContent('SGSIN');
  });

  it('読む人と申請できる人は違う（履歴は全員、申請は営業）', async () => {
    mockApi([request()]);

    renderPanel('IN_TRANSIT', false);

    expect(await screen.findByTestId('cancellation-history-CR-1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /キャンセル/ })).not.toBeInTheDocument();
  });

});
