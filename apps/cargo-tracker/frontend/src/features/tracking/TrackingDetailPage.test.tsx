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
    // **サーバは手で選べる先だけを返す**（誤配・例外発生は荷役と例外の起票が決める）。
    // モックを本物より甘くしない。
    nextStatuses: ['RECEIVED'],
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
