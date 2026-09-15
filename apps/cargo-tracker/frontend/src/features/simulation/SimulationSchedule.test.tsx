import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SimulationSchedulePage } from './SimulationSchedulePage';

/**
 * S94 継続実行と統計（US36 §受入基準 3・4・8）。
 *
 * <p><b>本物の HTTP の形で相手をする。</b> 204（動いていない）と 409（すでに動いて
 * いる）は画面の出し分けそのものなので、関数を差し替えると確かめたいものが
 * 変わる。</p>
 */
function respond(handler: (url: string, init?: RequestInit) => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) =>
    Promise.resolve(handler(String(input), init as RequestInit)),
  );
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/simulations/schedule']}>
        <Routes>
          <Route path="/admin/simulations/schedule" element={<SimulationSchedulePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function schedule(over: Record<string, unknown> = {}) {
  return {
    scheduleId: 'SCH-1',
    seed: 1789234512,
    intervalSeconds: 30,
    maxConcurrent: 2,
    exceptionRatio: 0.2,
    status: 'RUNNING',
    statusLabel: '実行中',
    startedBy: 'admin01',
    startedAt: '2026-09-15T01:00:00Z',
    stoppedAt: null,
    runningNow: 1,
    runsByStatus: [
      { code: 'SUCCEEDED', label: '成功', count: 18 },
      { code: 'FAILED', label: '失敗', count: 4 },
    ],
    failuresByStep: [{ code: 'ASSIGN_ROUTE', label: '経路の確定', count: 3 }],
    ...over,
  };
}

describe('S94 継続実行と統計', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('US36 §3: 乱数の種が読める（読めないと同じ並びを再現できない）', async () => {
    respond(() => new Response(JSON.stringify(schedule()), { status: 200 }));

    renderPage();

    expect(await screen.findByText('1789234512')).toBeInTheDocument();
  });

  it('US36 §8: 件数・成否の内訳・止まった工程の分布が出る', async () => {
    respond(() => new Response(JSON.stringify(schedule()), { status: 200 }));

    renderPage();

    expect(await screen.findByText('成功')).toBeInTheDocument();
    expect(screen.getByText('18')).toBeInTheDocument();
    // **どの工程で止まりやすいかが、いちばん見たい形である。**
    expect(screen.getByText('経路の確定')).toBeInTheDocument();
    expect(screen.getByText('1 / 2 本')).toBeInTheDocument();
  });

  it('US36 §4: 停止処理中は「止まった」と出さない（画面が嘘をつかない）', async () => {
    respond(() => new Response(
      JSON.stringify(schedule({ status: 'STOPPING', statusLabel: '停止処理中', runningNow: 1 })),
      { status: 200 },
    ));

    renderPage();

    expect(await screen.findByText('停止処理中')).toBeInTheDocument();
    expect(screen.getByText(/走っている実行が終わるまで待っています/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '継続実行を停止する' })).toBeDisabled();
  });

  it('US36 §4: 動いていなければ開始できる（204 を「見つかりません」の赤にしない）', async () => {
    const calls: string[] = [];
    respond((_url, init) => {
      if (init?.method === 'POST') {
        calls.push(String(init.body));
        return new Response(JSON.stringify({ scheduleId: 'SCH-2' }), { status: 201 });
      }
      return new Response(null, { status: 204 });
    });

    renderPage();
    await screen.findByText(/継続実行は動いていません/);
    await userEvent.type(screen.getByLabelText('乱数の種'), '42');
    await userEvent.click(screen.getByRole('button', { name: '継続実行を開始する' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toContain('42');
  });

  it('US36 §4: 止めると DELETE で送る', async () => {
    const methods: string[] = [];
    respond((_url, init) => {
      methods.push(init?.method ?? 'GET');
      if (init?.method === 'DELETE') {
        return new Response(null, { status: 202 });
      }
      return new Response(JSON.stringify(schedule()), { status: 200 });
    });

    renderPage();
    await screen.findByText('実行中');
    await userEvent.click(screen.getByRole('button', { name: '継続実行を停止する' }));

    await waitFor(() => expect(methods).toContain('DELETE'));
  });

  it('US36 §6: 断られた理由をそのまま出す（許可されていない環境）', async () => {
    respond((_url, init) => {
      if (init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            code: 'BUSINESS_RULE_VIOLATION',
            message: 'この環境では継続実行を開始できません（流し続ける側が業務を止めないようにするため）',
          }),
          { status: 422 },
        );
      }
      return new Response(null, { status: 204 });
    });

    renderPage();
    await screen.findByText(/継続実行は動いていません/);
    await userEvent.click(screen.getByRole('button', { name: '継続実行を開始する' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('この環境では継続実行を開始できません'),
    );
  });
});
