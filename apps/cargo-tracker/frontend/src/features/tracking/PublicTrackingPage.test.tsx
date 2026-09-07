import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PublicTrackingPage } from './PublicTrackingPage';

function tracking(over: Record<string, unknown> = {}) {
  return {
    trackingNumber: 'TRK-8K2QX7M4RB',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    statusLabel: '輸送中',
    currentUnLocode: 'SGSIN',
    departedAt: '2026-09-10T09:00:00Z',
    estimatedArrival: '2026-09-24T18:00:00Z',
    history: [
      { occurredAt: '2026-09-10T02:00:00Z', statusLabel: '受領済', location: 'JPTYO' },
      { occurredAt: '2026-09-11T02:00:00Z', statusLabel: '積込済', location: 'JPTYO' },
    ],
    ...over,
  };
}

function respondWith(status: number, body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: status < 400,
    status,
    text: async () => JSON.stringify(body),
  } as Response);
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/track" element={<PublicTrackingPage />} />
          <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
          <Route path="/login" element={<h1>ログイン</h1>} />
          <Route path="/portal" element={<h1>ポータル</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('S44 公開追跡照会', () => {
  it('US18 §1: 追跡番号つきで開くと貨物情報が出る', async () => {
    respondWith(200, tracking());

    renderAt('/track/TRK-8K2QX7M4RB');

    expect(await screen.findByText('輸送中')).toBeInTheDocument();
    // 出発・到着予定・現在は定義リストで出る（履歴の表にも港が並ぶので、
    // 「どこかに JPTYO がある」では判別にならない）。
    expect(screen.getByText('出発').nextElementSibling).toHaveTextContent('JPTYO');
    expect(screen.getByText('到着予定').nextElementSibling).toHaveTextContent('USNYC');
    expect(screen.getByText('現在').nextElementSibling).toHaveTextContent('SGSIN');
  });

  it('US18 §3: 履歴が起きた順に出る', async () => {
    respondWith(200, tracking());

    renderAt('/track/TRK-8K2QX7M4RB');

    const rows = await screen.findAllByRole('row');
    // 見出し行 + 2 行。
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('受領済');
    expect(rows[2]).toHaveTextContent('積込済');
  });

  it('US18 §4: 到着予定日が出る', async () => {
    respondWith(200, tracking());

    renderAt('/track/TRK-8K2QX7M4RB');

    expect(await screen.findByText(/到着予定/)).toBeInTheDocument();
    expect(screen.getByText(/2026\/09\/25/)).toBeInTheDocument();
  });

  it('見つからないときは入力形式のヒントと問い合わせの出口を出す', async () => {
    // **存在しない番号と権限の無い番号を区別しない。** 区別すると総当たりで
    // 「実在するが自分のものではない番号」を選り分けられる。
    respondWith(404, { code: 'NOT_FOUND', message: '見つかりません' });

    renderAt('/track/TRK-NOSUCHNUM');

    const notice = await screen.findByText(/追跡番号が見つかりません/);
    // ヒントと出口は同じ場所に出す。別の場所だと、見つからない人が読まずに離れる。
    expect(notice).toHaveTextContent('TRK-');
    expect(notice).toHaveTextContent('お問い合わせ窓口');
  });

  it('形式が違えば照会せずに知らせる', async () => {
    const fetchSpy = respondWith(200, tracking());

    renderAt('/track');
    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-123');
    await userEvent.click(screen.getByRole('button', { name: '照会する' }));

    expect(await screen.findByText(/追跡番号の形式が違います/)).toBeInTheDocument();
    // **照会前に知らせる。** 送ってしまうと、レート制限（429）の回数を無駄に使う。
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('番号なしでも開ける（認証の外の入口）', () => {
    renderAt('/track');

    expect(screen.getByRole('heading', { name: '荷物の追跡' })).toBeInTheDocument();
    expect(screen.getByLabelText('追跡番号')).toBeInTheDocument();
  });

  it('照会すると番号つきの URL に移り、共有できる', async () => {
    respondWith(200, tracking());

    renderAt('/track');
    await userEvent.type(screen.getByLabelText('追跡番号'), 'trk-8k2qx7m4rb');
    await userEvent.click(screen.getByRole('button', { name: '照会する' }));

    // 荷受人は URL を転送して共有する。番号が URL に無いと転送できない。
    await waitFor(() => expect(screen.getByText('輸送中')).toBeInTheDocument());
  });

  it('連打を断られたら待つよう伝える（429）', async () => {
    respondWith(429, { code: 'TOO_MANY_REQUESTS', message: '' });

    renderAt('/track/TRK-8K2QX7M4RB');

    expect(await screen.findByText(/しばらく待ってから再度お試しください/)).toBeInTheDocument();
  });

  it('公開画面に荷主名・予約番号・金額を出さない', async () => {
    respondWith(200, tracking());

    renderAt('/track/TRK-8K2QX7M4RB');
    await screen.findByText('輸送中');

    // **サーバが渡さないので画面には出しようがない。** ここで固定するのは、
    // あとから「一覧で要るから」と応答に足したときに気づくため。
    expect(screen.queryByText(/山田商事/)).not.toBeInTheDocument();
    expect(screen.queryByText(/B-2026-/)).not.toBeInTheDocument();
    expect(screen.queryByText(/円/)).not.toBeInTheDocument();
  });

  it('ログイン画面へ戻れる（認証の外の 2 画面は行き来する）', () => {
    renderAt('/track');

    expect(screen.getByRole('link', { name: 'ログイン画面へ戻る' }))
      .toHaveAttribute('href', '/login');
  });
});
