import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TrackingDetailPage } from './TrackingDetailPage';
import { useAuthStore } from '@/shared/auth/authStore';

function tracking(over: Record<string, unknown> = {}) {
  return {
    trackingNumber: 'TRK-8K2QX7M4RB',
    bookingId: 'b-1',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    cargoType: 'GENERAL',
    status: 'NOT_RECEIVED',
    statusLabel: '未受領',
    currentUnLocode: null,
    estimatedArrival: '2026-09-24T18:00:00Z',
    lastStatusChangedAt: '2026-09-08T01:00:00Z',
    history: [],
    exceptions: [],
    // **サーバは手で選べる先だけを返す**（誤配・例外発生は荷役と例外の起票が決める）。
    // モックを本物より甘くしない。
    nextStatuses: ['RECEIVED'],
    misrouted: false,
    ...over,
  };
}

function shipperTracking(over: Record<string, unknown> = {}) {
  return tracking(over);
}

function respondWith(body: unknown, status = 200) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: status < 400,
    status,
    text: async () => JSON.stringify(body),
  } as Response);
}

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/tracking/TRK-8K2QX7M4RB']}>
        <Routes>
          <Route path="/tracking/:trackingNumber" element={<TrackingDetailPage />} />
          <Route path="/tracking" element={<h1>追跡</h1>} />
          <Route path="/bookings/:bookingId" element={<h1>予約詳細</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function asTracker() {
  useAuthStore.setState({ user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' } });
}

function asShipper() {
  useAuthStore.setState({
    user: { username: 'shipper01', roles: ['ROLE_SHIPPER'], token: 't' },
  });
}

beforeEach(asTracker);
afterEach(() => vi.restoreAllMocks());

describe('S41 追跡詳細・管理', () => {
  it('US17 §1: 追跡番号を指定して現在の貨物情報を確認できる', async () => {
    respondWith(tracking());

    renderDetail();

    expect(await screen.findByText('未受領')).toBeInTheDocument();
    expect(screen.getByText(/JPTYO/)).toBeInTheDocument();
  });

  it('US17 §3: 状態の履歴が出る（誰が動かしたかも）', async () => {
    respondWith(tracking({
      status: 'RECEIVED',
      statusLabel: '受領済',
      history: [{
        occurredAt: '2026-09-10T02:00:00Z',
        eventType: 'MANUAL',
        previousStatusLabel: '未受領',
        statusLabel: '受領済',
        location: 'JPTYO',
        recordedBy: 'tracker01',
      }],
    }));

    renderDetail();

    const row = await screen.findByRole('row', { name: /受領済/ });
    expect(row).toHaveTextContent('tracker01');
    expect(row).toHaveTextContent('JPTYO');
  });

  it('US17 §2: 動かせる先だけを選べる（画面が遷移表を持たない）', async () => {
    respondWith(tracking());

    renderDetail();

    const select = await screen.findByLabelText('新しい状態');
    const options = Array.from(select.querySelectorAll('option')).map((o) => o.textContent);
    // サーバが集約と同じ述語で決めた先だけ。押してから断られない。
    expect(options).toContain('受領済');
    expect(options).not.toContain('引取済');
    // **誤配・例外発生は手で選べない。** サーバが返さないので出ない。
    expect(options).not.toContain('誤配');
    expect(options).not.toContain('例外発生');
  });

  it('US17 §2: 状態を更新すると送信される', async () => {
    const fetchSpy = respondWith(tracking());

    renderDetail();
    await screen.findByLabelText('新しい状態');
    await userEvent.selectOptions(screen.getByLabelText('新しい状態'), 'RECEIVED');
    await userEvent.type(screen.getByLabelText('場所（UN/LOCODE）'), 'JPTYO');
    await userEvent.click(screen.getByRole('button', { name: '状態を更新する' }));

    await waitFor(() => {
      const posted = fetchSpy.mock.calls.find((call) => call[1]?.method === 'POST');
      expect(posted?.[0]).toContain('/tracking/trackings/TRK-8K2QX7M4RB/status');
      expect(String(posted?.[1]?.body)).toContain('RECEIVED');
    });
  });

  it('荷主には更新の操作を出さない（自分の貨物の状態を書き換えられてしまう）', async () => {
    asShipper();
    respondWith(tracking());

    renderDetail();

    await screen.findByText('未受領');
    expect(screen.queryByRole('button', { name: '状態を更新する' })).not.toBeInTheDocument();
  });

  it('動かせる先が無いときは操作を出さない（引取済からは動かない）', async () => {
    respondWith(tracking({ status: 'DELIVERED', statusLabel: '引取済', nextStatuses: [] }));

    renderDetail();

    await screen.findByText('引取済');
    expect(screen.queryByRole('button', { name: '状態を更新する' })).not.toBeInTheDocument();
    expect(screen.getByText(/これ以上状態は動きません/)).toBeInTheDocument();
  });

  it('例外の対応中も操作を出さない（押しても断られるボタンを並べない）', async () => {
    // サーバは例外中の nextStatuses を空で返す。画面が遷移表を持たないので、
    // ここは「空なら操作を出さない」だけを守ればよい。
    respondWith(tracking({ status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [] }));

    renderDetail();

    await screen.findByText('例外発生');
    expect(screen.queryByRole('button', { name: '状態を更新する' })).not.toBeInTheDocument();
  });

  it('一覧と予約に戻れる', async () => {
    respondWith(tracking());

    renderDetail();

    expect(await screen.findByRole('link', { name: '追跡一覧に戻る' }))
      .toHaveAttribute('href', '/tracking');
    expect(screen.getByRole('link', { name: '予約を見る' }))
      .toHaveAttribute('href', '/bookings/b-1');
  });

  it('荷主には予約へのリンクを出さない（403 になる）', async () => {
    // 共有画面のリンクもロールで出し分ける（IT6 の教訓）。
    asShipper();
    respondWith(tracking());

    renderDetail();

    await screen.findByText('未受領');
    expect(screen.queryByRole('link', { name: '予約を見る' })).not.toBeInTheDocument();
  });

  it('見つからないときは一覧へ戻す出口を出す', async () => {
    respondWith({ code: 'NOT_FOUND', message: '見つかりません' }, 404);

    renderDetail();

    expect(await screen.findByText(/追跡番号が見つかりません/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '追跡一覧に戻る' })).toBeInTheDocument();
  });

  it('荷主には記録者（社内の担当者名）を出さない', async () => {
    // S41 は荷主も開く。ui_design は荷主向けで「担当者名は出しません」と定めている。
    asShipper();
    respondWith(shipperTracking({
      history: [{
        occurredAt: '2026-09-10T02:00:00Z',
        eventType: 'MANUAL',
        previousStatusLabel: '未受領',
        statusLabel: '受領済',
        location: 'JPTYO',
        recordedBy: 'tracker01',
      }],
    }));

    renderDetail();

    await screen.findByRole('row', { name: /受領済/ });
    expect(screen.queryByText('tracker01')).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: '記録者' })).not.toBeInTheDocument();
  });

  it('US17 §2: 起きた日時を後から入れられる（出港は夜間、記録は翌朝）', async () => {
    const fetchSpy = respondWith(tracking());

    renderDetail();
    await screen.findByLabelText('新しい状態');
    await userEvent.selectOptions(screen.getByLabelText('新しい状態'), 'RECEIVED');
    await userEvent.type(screen.getByLabelText('起きた日時'), '2026-09-10T22:30');
    await userEvent.click(screen.getByRole('button', { name: '状態を更新する' }));

    await waitFor(() => {
      const posted = fetchSpy.mock.calls.find((call) => call[1]?.method === 'POST');
      // 業務タイムゾーンで解釈して送る（ブラウザの時計に依らない）。
      expect(String(posted?.[1]?.body)).toContain('2026-09-10T13:30');
    });
  });
});

describe('S41 例外の対応（US19 §3・§4 / IT10 T7）', () => {
  it('追跡管理者は詳細から例外を起票しに行ける（追跡番号を書き写させない）', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    respondWith(tracking());

    renderDetail();

    expect(await screen.findByRole('link', { name: '例外を起票する' }))
      .toHaveAttribute('href', '/tracking/TRK-8K2QX7M4RB/exceptions/new');
  });

  it('荷主には起票の導線を出さない（403 になる）', async () => {
    useAuthStore.setState({
      user: { username: 'shipper01', roles: ['ROLE_SHIPPER'], token: 't' },
    });
    respondWith(tracking());

    renderDetail();

    await screen.findByText('未受領');
    expect(screen.queryByRole('link', { name: '例外を起票する' })).not.toBeInTheDocument();
  });

  function openException(over: Record<string, unknown> = {}) {
    return {
      exceptionId: 'ex-1',
      exceptionType: 'DELAY',
      exceptionTypeLabel: '遅延',
      responseStatus: 'REPORTED',
      responseStatusLabel: '起票',
      urgent: false,
      unLocode: 'SGSIN',
      description: '台風で 3 日遅れます',
      resolution: null,
      newEstimatedArrival: null,
      responsePlan: null,
      occurredAt: '2026-09-20T02:00:00Z',
      resolvedAt: null,
      settled: false,
      notifications: [],
      ...over,
    };
  }

  it('起票された例外が詳細に出る（対応する人が読んで動ける）', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    respondWith(tracking({
      status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
      exceptions: [{
        exceptionId: 'ex-1',
        exceptionType: 'DELAY',
        exceptionTypeLabel: '遅延',
        responseStatus: 'REPORTED',
        responseStatusLabel: '起票',
        urgent: false,
        unLocode: 'SGSIN',
        description: '台風で 3 日遅れます',
        resolution: null,
        newEstimatedArrival: null,
        responsePlan: null,
        occurredAt: '2026-09-20T02:00:00Z',
        resolvedAt: null,
        settled: false,
        notifications: [],
      }],
    }));

    renderDetail();

    expect(await screen.findByText('台風で 3 日遅れます')).toBeInTheDocument();
    expect(screen.getByText('遅延')).toBeInTheDocument();
  });

  it('US19 §3: 荷主へ知らせた記録が読める（記録だけして読めなければ、記録していないのと同じ）', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    respondWith(tracking({
      exceptions: [openException({
        notifications: [{
          means: '電話',
          summary: '3 日遅れる見込みと伝えました',
          notifiedBy: 'tracker01',
          notifiedAt: '2026-09-20T05:00:00Z',
        }],
      })],
    }));

    renderDetail();

    expect(await screen.findByText('3 日遅れる見込みと伝えました')).toBeInTheDocument();
    expect(screen.getByText('電話')).toBeInTheDocument();
  });

  it('US19 §4: 対応方針と新しい到着予定日が読める（入力した値が消えない）', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    respondWith(tracking({
      exceptions: [openException({
        responseStatus: 'RESPONDING',
        responseStatusLabel: '対応中',
        responsePlan: '代替便を手配中',
        newEstimatedArrival: '2026-09-27',
      })],
    }));

    renderDetail();

    expect(await screen.findByText('代替便を手配中')).toBeInTheDocument();
    expect(screen.getByText('2026-09-27')).toBeInTheDocument();
  });

  it('解決した例外も残る（事実は消えない・不変条件 6）', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    respondWith(tracking({
      exceptions: [openException({
        responseStatus: 'RESOLVED',
        responseStatusLabel: '解決',
        resolution: '代替便に振り替えました',
        resolvedAt: '2026-09-21T02:00:00Z',
        // **モックを本物より甘くしない。** サーバは決着を settled で返す。
        settled: true,
      })],
    }));

    renderDetail();

    expect(await screen.findByText('代替便に振り替えました')).toBeInTheDocument();
    // 解決したものに操作は出さない（押しても断られるボタンを並べない）。
    expect(screen.queryByRole('button', { name: '対応を始める' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '解決にする' })).not.toBeInTheDocument();
  });

  it('荷主には例外の操作を出さない（対応するのは追跡管理者）', async () => {
    useAuthStore.setState({
      user: { username: 'shipper01', roles: ['ROLE_SHIPPER'], token: 't' },
    });
    respondWith(tracking({
      status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
      exceptions: [openException()],
    }));

    renderDetail();

    // 起きていることは読める。手を入れるのは追跡管理者だけ。
    expect(await screen.findByText('台風で 3 日遅れます')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '対応を始める' })).not.toBeInTheDocument();
  });

  it('US19 §4: 対応を始めると、新しい到着予定日と対応方針が送られる', async () => {
    // **§4 の唯一の入力経路。** 押して送る検査が無いと、組み立てを潰しても緑。
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'POST') {
        return { ok: true, status: 204, text: async () => '' } as Response;
      }
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify(tracking({
          status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
          exceptions: [openException()],
        })),
      } as Response;
    });

    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: '対応を始める' }));
    await userEvent.type(screen.getByLabelText('新しい到着予定日'), '2026-09-27');
    await userEvent.type(screen.getByLabelText('対応方針'), '代替便を手配中');
    await userEvent.click(screen.getByRole('button', { name: '対応の開始を記録する' }));

    await waitFor(() => {
      const post = fetchSpy.mock.calls.find(([, init]) => init?.method === 'POST');
      expect(String(post?.[0])).toContain('/exceptions/ex-1/response');
      const body = JSON.parse(String(post?.[1]?.body));
      expect(body.newEstimatedArrival).toBe('2026-09-27');
      expect(body.plan).toBe('代替便を手配中');
    });
  });

  it('対応内容を入れて解決すると、その内容が送られる', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'POST') {
        return { ok: true, status: 204, text: async () => '' } as Response;
      }
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify(tracking({
          status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
          exceptions: [openException()],
        })),
      } as Response;
    });

    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: '解決にする' }));
    await userEvent.type(screen.getByLabelText('対応内容'), '代替便に振り替えました');
    await userEvent.click(screen.getByRole('button', { name: '解決を確定する' }));

    await waitFor(() => {
      const post = fetchSpy.mock.calls.find(([, init]) => init?.method === 'POST');
      expect(post).toBeDefined();
      expect(String(post?.[0])).toContain('/exceptions/ex-1/resolution');
      expect(JSON.parse(String(post?.[1]?.body)).resolution).toBe('代替便に振り替えました');
    });
  });

  it('US19 §3: 荷主へ知らせた事実を記録できる（送信基盤はスコープ外）', async () => {
    useAuthStore.setState({
      user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' },
    });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'POST') {
        return { ok: true, status: 204, text: async () => '' } as Response;
      }
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify(tracking({
          status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
          exceptions: [openException()],
        })),
      } as Response;
    });

    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: '荷主へ知らせた' }));
    await userEvent.type(screen.getByLabelText('伝えた手段'), '電話');
    await userEvent.type(screen.getByLabelText('伝えた内容'), '3 日遅れる見込み');
    await userEvent.click(screen.getByRole('button', { name: '記録を残す' }));

    await waitFor(() => {
      const post = fetchSpy.mock.calls.find(([, init]) => init?.method === 'POST');
      expect(String(post?.[0])).toContain('/exceptions/ex-1/notifications');
      const body = JSON.parse(String(post?.[1]?.body));
      expect(body.means).toBe('電話');
      expect(body.summary).toBe('3 日遅れる見込み');
    });
  });
});

describe('S41 誤配バナー（US28 §受入基準 3・4）', () => {
  it('誤配なら現在地つきのバナーを出す', async () => {
    // **状態から導かない。** 例外の対応中は状態が「例外発生」へ退避するが、
    // 誤配であることは変わらない。
    respondWith(tracking({
      misrouted: true, status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
      currentUnLocode: 'SGSIN',
    }));

    renderDetail();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('誤配を検知しました');
    expect(alert).toHaveTextContent('SGSIN');
  });

  it('経路設計者には [経路を再設計] を出す', async () => {
    useAuthStore.setState({
      user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' },
    });
    respondWith(tracking({ misrouted: true }));

    renderDetail();

    expect(await screen.findByRole('link', { name: '経路を再設計' })).toBeInTheDocument();
  });

  it('誤配でなければバナーを出さない', async () => {
    respondWith(tracking());

    renderDetail();

    await screen.findByText('未受領');
    expect(screen.queryByText('誤配を検知しました。')).not.toBeInTheDocument();
  });
});

describe('S41 対応の入力中は読み直しを止める（IT11 レビュー / クラスタで実測）', () => {
  it('入力を始めたら 30 秒ごとの読み直しが止まる', async () => {
    // **書いている最中に入力欄が消える。** 追跡管理者は長い対応内容を書くので、
    // 書き終わる前に必ずポーリングに当たる（クラスタ E2E がここで落ちた）。
    const spy = respondWith(tracking({
      status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
      exceptions: [{
        exceptionId: 'ex-1',
        exceptionType: 'DELAY',
        exceptionTypeLabel: '遅延',
        responseStatus: 'REPORTED',
        responseStatusLabel: '起票',
        urgent: false,
        unLocode: 'SGSIN',
        description: '台風で 3 日遅れます',
        resolution: null,
        newEstimatedArrival: null,
        responsePlan: null,
        occurredAt: '2026-09-20T02:00:00Z',
        resolvedAt: null,
        settled: false,
        notifications: [],
      }],
    }));

    renderDetail();
    await screen.findByRole('button', { name: '解決にする' });
    await userEvent.click(screen.getByRole('button', { name: '解決にする' }));
    // 読み込みが済んでから時計を差し替える（差し替えたまま描くと初回の取得が進まない）。
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const before = spy.mock.calls.length;

    await vi.advanceTimersByTimeAsync(60_000);

    expect(spy.mock.calls.length).toBe(before);
    // 入力欄は残ったまま（描き直されていない）。
    expect(screen.getByLabelText('対応内容')).toBeInTheDocument();
    vi.useRealTimers();
  });
});

describe('S41 送信の完了処理は、開いた別のフォームを閉じない（IT11 クラスタで実測）', () => {
  it('荷主への記録のあとに解決を開いても、フォームが消えない', async () => {
    // **無条件に閉じると、その間に開いた別のフォームまで閉じる。** 記録の完了処理が
    // 遅れて届くと、開いたばかりの解決フォームが消え、画面には「押したのに何も
    // 起きない」としか出ない。
    let resolvePost: ((value: Response) => void) | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'POST') {
        // 記録の応答をこちらの合図まで遅らせる。
        return await new Promise<Response>((done) => { resolvePost = done; });
      }
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify(tracking({
          status: 'EXCEPTION', statusLabel: '例外発生', nextStatuses: [],
          exceptions: [{
            exceptionId: 'ex-1',
            exceptionType: 'DELAY',
            exceptionTypeLabel: '遅延',
            responseStatus: 'REPORTED',
            responseStatusLabel: '起票',
            urgent: false,
            unLocode: 'SGSIN',
            description: '台風で 3 日遅れます',
            resolution: null,
            newEstimatedArrival: null,
            responsePlan: null,
            occurredAt: '2026-09-20T02:00:00Z',
            resolvedAt: null,
            settled: false,
            notifications: [],
          }],
        })),
      } as Response;
    });

    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: '荷主へ知らせた' }));
    await userEvent.type(screen.getByLabelText('伝えた手段'), '電話');
    await userEvent.type(screen.getByLabelText('伝えた内容'), '遅れます');
    await userEvent.click(screen.getByRole('button', { name: '記録を残す' }));

    // 記録が返る前に、続けて解決を開く。
    await userEvent.click(screen.getByRole('button', { name: '解決にする' }));
    expect(screen.getByLabelText('対応内容')).toBeInTheDocument();

    // ここで記録の応答が届く。**解決フォームは開いたまま**でなければならない。
    resolvePost?.(new Response(null, { status: 204 }));

    await vi.waitFor(() => {
      expect(screen.getByLabelText('対応内容')).toBeInTheDocument();
    });
  });
});
