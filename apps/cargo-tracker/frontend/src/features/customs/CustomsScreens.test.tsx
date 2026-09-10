import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CustomsListPage } from './CustomsListPage';
import { CustomsDetailPage } from './CustomsDetailPage';
import { CustomsRegisterPage } from './CustomsRegisterPage';
import { useAuthStore } from '@/shared/auth/authStore';

function declaration(over: Record<string, unknown> = {}) {
  return {
    declarationNumber: 'IMP-2026-0001',
    trackingNumber: 'TRK-8K2QX7M4RB',
    bookingId: 'b-1',
    status: 'PENDING',
    statusLabel: '審査中',
    declaredAt: '2026-10-01T09:00:00Z',
    lastStatusChangedAt: '2026-10-01T09:00:00Z',
    lastHeldAt: null,
    heldBusinessDays: 0,
    overdue: false,
    lastReason: null,
    changedBy: null,
    ...over,
  };
}

function held(over: Record<string, unknown> = {}) {
  return declaration({
    status: 'HELD',
    statusLabel: '留置',
    lastHeldAt: '2026-10-02T09:00:00Z',
    heldBusinessDays: 4,
    overdue: true,
    lastReason: '原産地証明が未提出',
    changedBy: 'tracker01',
    ...over,
  });
}

function renderAt(path: string, element: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={path.split('?')[0] as string} element={element} />
          <Route path="/customs" element={<h1>通関申告一覧</h1>} />
        </Routes>
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

describe('S52 通関申告一覧', () => {
  it('既定で通関済を外して問い合わせる（決着したものが混ざると一覧が信用されない）', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [declaration()], total: 1 }), { status: 200 }),
    );

    renderAt('/customs', <CustomsListPage />);

    expect(await screen.findByText('IMP-2026-0001')).toBeInTheDocument();
    const url = String(fetchSpy.mock.calls[0]?.[0]);
    expect(url).toContain('includeCleared=false');
  });

  it('留置は営業日数で出す（暦日と読まれると 3 日超の意味が変わる）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [held()], total: 1 }), { status: 200 }),
    );

    renderAt('/customs', <CustomsListPage />);

    expect(await screen.findByText('4 営業日')).toBeInTheDocument();
  });

  it('督促の対象があると件数を知らせる（気づく手段は次の行動へ繋ぐ）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [held()], total: 1 }), { status: 200 }),
    );

    renderAt('/customs', <CustomsListPage />);

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('留置が 3 営業日を超えた申告が 1 件あります');
  });

  it('督促の対象だけに絞れる（絞りはサーバに渡す。画面で間引かない）', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [held()], total: 1 }), { status: 200 }),
    );

    renderAt('/customs', <CustomsListPage />);
    expect(await screen.findByText('IMP-2026-0001')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('checkbox', { name: '督促の対象だけ表示' }));

    await waitFor(() =>
      expect(fetchSpy.mock.calls.some((call) => String(call[0]).includes('overdueOnly=true')))
        .toBe(true));
  });

  it('追跡番号と通関状態で絞れる', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 }),
    );

    renderAt('/customs', <CustomsListPage />);
    await userEvent.type(screen.getByLabelText('追跡番号'), 'trk-abc');
    await userEvent.selectOptions(screen.getByLabelText('通関状態'), 'HELD');

    await waitFor(() => {
      const urls = fetchSpy.mock.calls.map((call) => String(call[0]));
      expect(urls.some((url) => url.includes('trackingNumber=TRK-ABC'))).toBe(true);
      expect(urls.some((url) => url.includes('status=HELD'))).toBe(true);
    });
  });

  it('登録の導線は追跡ロールには出さない（開けない場所へ誘わない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [declaration()], total: 1 }), { status: 200 }),
    );

    renderAt('/customs', <CustomsListPage />);

    expect(await screen.findByText('IMP-2026-0001')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '通関申告を登録する' })).not.toBeInTheDocument();
  });

  it('登録の導線は荷役ロールに出す（申告を出すのは荷役作業員）', async () => {
    // **1 つのテストで 2 度描かない。** 前の DOM が残るので、同じリンクが
    // 2 つ見つかって「出ている」の意味が変わる。
    useAuthStore.setState({
      user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' },
    });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [declaration()], total: 1 }), { status: 200 }),
    );

    renderAt('/customs', <CustomsListPage />);

    expect(await screen.findByRole('link', { name: '通関申告を登録する' }))
      .toBeInTheDocument();
  });
});

describe('S53 通関申告', () => {
  function detailResponses(view: Record<string, unknown>, entries: unknown[]) {
    return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/history')) {
        return Promise.resolve(new Response(JSON.stringify({ items: entries }),
          { status: 200 }));
      }
      return Promise.resolve(new Response(JSON.stringify(view), { status: 200 }));
    });
  }

  it('US29 §8: 履歴が日時・変更・変更者・理由で読める', async () => {
    detailResponses(held(), [
      {
        kind: 'REGISTERED', previousStatus: null, status: 'PENDING', statusLabel: '審査中',
        reason: '通関申告を登録しました', changedBy: 'handler01',
        changedAt: '2026-10-01T09:00:00Z',
      },
      {
        kind: 'STATUS_CHANGED', previousStatus: 'PENDING', status: 'HELD', statusLabel: '留置',
        reason: '原産地証明が未提出', changedBy: 'tracker01',
        changedAt: '2026-10-02T09:00:00Z',
      },
    ]);

    renderAt('/customs/IMP-2026-0001', <CustomsDetailPage />);

    expect(await screen.findByText('原産地証明が未提出')).toBeInTheDocument();
    expect(screen.getByText('tracker01')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '理由' })).toBeInTheDocument();
  });

  it('US29 §4: 通関完了を知らせた記録も履歴に出る（記録と読み口は対で出す）', async () => {
    detailResponses(declaration({ status: 'CLEARED', statusLabel: '通関済' }), [
      {
        kind: 'CLEARANCE_NOTIFIED', previousStatus: null, status: null, statusLabel: null,
        reason: '通関が完了しました（申告番号 IMP-2026-0001）', changedBy: null,
        changedAt: '2026-10-03T09:00:00Z',
      },
    ]);

    renderAt('/customs/IMP-2026-0001', <CustomsDetailPage />);

    expect(await screen.findByText('通関完了の連絡')).toBeInTheDocument();
  });

  it('督促の対象なら警告を出す', async () => {
    detailResponses(held(), []);

    renderAt('/customs/IMP-2026-0001', <CustomsDetailPage />);

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('留置が 3 営業日を超えています');
  });

  it('US29 §2: 理由を添えて状態を更新でき、送信中を出す', async () => {
    const fetchSpy = detailResponses(declaration(), []);

    renderAt('/customs/IMP-2026-0001', <CustomsDetailPage />);
    await screen.findByRole('heading', { name: '状態を更新する' });
    await userEvent.selectOptions(screen.getByLabelText('状態の変更'), 'HELD');
    await userEvent.type(screen.getByLabelText('理由'), '原産地証明が未提出');
    await userEvent.click(screen.getByRole('button', { name: '状態を更新する' }));

    await waitFor(() => {
      const post = fetchSpy.mock.calls.find((call) =>
        String(call[0]).includes('/status'));
      expect(post).toBeDefined();
      expect(String(post?.[1]?.body)).toContain('原産地証明が未提出');
      expect(String(post?.[1]?.body)).toContain('HELD');
    });
  });

  it('審査中は選べない（押せるのに断られる操作を並べない）', async () => {
    detailResponses(declaration(), []);

    renderAt('/customs/IMP-2026-0001', <CustomsDetailPage />);
    const select = await screen.findByLabelText('状態の変更');

    expect(Array.from(select.querySelectorAll('option')).map((option) => option.textContent))
      .toEqual(['通関済', '留置', '不可']);
  });

  it('更新のフォームは追跡ロールにだけ出す（荷役は申告を出す側）', async () => {
    useAuthStore.setState({
      user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' },
    });
    detailResponses(declaration(), []);

    renderAt('/customs/IMP-2026-0001', <CustomsDetailPage />);

    expect(await screen.findByText('審査中')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '状態を更新する' })).not.toBeInTheDocument();
  });
});

describe('S53 通関申告の登録', () => {
  it('US29 §1: 申告番号・追跡番号・申告日時を送る', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 201 }),
    );

    renderAt('/customs/new', <CustomsRegisterPage />);
    await userEvent.type(screen.getByLabelText('申告番号'), 'IMP-2026-0009');
    await userEvent.type(screen.getByLabelText('追跡番号'), 'trk-8k2qx7m4rb');
    await userEvent.type(screen.getByLabelText('申告日時'), '2026-10-01T09:00');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => {
      const body = String(fetchSpy.mock.calls[0]?.[1]?.body);
      expect(body).toContain('IMP-2026-0009');
      // 追跡番号は大文字に揃える（現場は小文字で打つ）。
      expect(body).toContain('TRK-8K2QX7M4RB');
    });
  });

  it('断られた理由をそのまま出す（直し方が理由の中にある）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({
        code: 'BUSINESS_RULE_VIOLATION',
        message: '追跡番号 TRK-X には決着していない通関申告（IMP-1）があります。'
          + '先にその申告の状態を更新してください',
      }), { status: 422 }),
    );

    renderAt('/customs/new', <CustomsRegisterPage />);
    await userEvent.type(screen.getByLabelText('申告番号'), 'IMP-2026-0010');
    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-X');
    await userEvent.type(screen.getByLabelText('申告日時'), '2026-10-01T09:00');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('先にその申告の状態を更新してください');
  });
});
