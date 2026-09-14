import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SimulationListPage } from './SimulationListPage';
import { SimulationRunPage } from './SimulationRunPage';
import { useAuthStore } from '@/shared/auth/authStore';

function summary(over: Record<string, unknown> = {}) {
  return {
    runId: 'SIM-1',
    scenarioLabel: '一般貨物の標準輸送',
    status: 'RUNNING',
    statusLabel: '実行中',
    startedAt: '2026-09-14T01:00:00Z',
    finishedAt: null,
    startedBy: 'admin01',
    succeededSteps: 4,
    plannedSteps: 13,
    ...over,
  };
}

function step(over: Record<string, unknown> = {}) {
  return {
    stepNo: 1,
    kind: 'REGISTER_BOOKING',
    kindLabel: '予約の登録',
    outcome: 'SUCCEEDED',
    outcomeLabel: '成功',
    elapsedMs: 120,
    producedId: 'BK-1',
    failureStatus: null,
    failureMessage: null,
    occurredAt: '2026-09-14T01:00:01Z',
    ...over,
  };
}

function respond(handler: (url: string, init?: RequestInit) => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) =>
    Promise.resolve(handler(String(input), init as RequestInit)),
  );
}

function renderList() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/simulations']}>
        <Routes>
          <Route path="/admin/simulations" element={<SimulationListPage />} />
          <Route path="/admin/simulations/:runId" element={<SimulationRunPage />} />
          <Route path="/bookings/:bookingId" element={<p>予約詳細</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderRun(runId = 'SIM-1') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/admin/simulations/${runId}`]}>
        <Routes>
          <Route path="/admin/simulations/:runId" element={<SimulationRunPage />} />
          <Route path="/bookings/:bookingId" element={<p>予約詳細</p>} />
          <Route path="/tracking" element={<p>追跡一覧</p>} />
          <Route path="/invoices" element={<p>請求一覧</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'admin01', roles: ['ROLE_ADMIN'], token: 't' } });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('S92 業務シミュレーション', () => {
  it('どこまで進んだかが一覧から読める（開かないと分からない形にしない）', async () => {
    respond(() => new Response(JSON.stringify({ items: [summary()] }), { status: 200 }));

    renderList();

    expect(await screen.findByText('4 / 13 工程')).toBeInTheDocument();
    expect(screen.getByText('実行中')).toBeInTheDocument();
  });

  it('US33 §1: 一覧からシナリオを選んで実行し、その結果へ移る', async () => {
    const calls: string[] = [];
    respond((url, init) => {
      calls.push(`${init?.method ?? 'GET'} ${url} ${String(init?.body ?? '')}`);
      if (url.endsWith('/simulation/runs') && init?.method === 'POST') {
        return new Response(JSON.stringify({ runId: 'SIM-9' }), { status: 201 });
      }
      if (url.includes('/simulation/runs/SIM-9')) {
        return new Response(
          JSON.stringify({
            runId: 'SIM-9',
            scenario: 'STANDARD',
            scenarioLabel: '一般貨物の標準輸送',
            status: 'RUNNING',
            statusLabel: '実行中',
            seed: null,
            startedAt: '2026-09-14T01:00:00Z',
            finishedAt: null,
            startedBy: 'admin01',
            steps: [step()],
          }),
          { status: 200 },
        );
      }
      return new Response(JSON.stringify({ items: [] }), { status: 200 });
    });

    renderList();
    await screen.findByText('まだ実行していません。');
    await userEvent.click(screen.getByRole('button', { name: '実行する' }));

    // **一覧から始める。** 識別子を握って API を叩く形では、一覧から辿れない
    // 欠陥を踏まない（IT15 Try T2）。
    expect(await screen.findByText('実行結果')).toBeInTheDocument();
    expect(calls).toContainEqual(
      expect.stringContaining('"scenario":"一般貨物の標準輸送"'),
    );
  });

  it('US33 §5: 二重実行は断りの理由をそのまま出す（実行中の識別子ごと）', async () => {
    respond((url, init) => {
      if (url.endsWith('/simulation/runs') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            code: 'ILLEGAL_TRANSITION',
            message:
              'シナリオ「一般貨物の標準輸送」は実行中です（実行 SIM-1）。その結果を開いてください',
          }),
          { status: 409 },
        );
      }
      return new Response(JSON.stringify({ items: [summary()] }), { status: 200 });
    });

    renderList();
    await screen.findByText('4 / 13 工程');
    await userEvent.click(screen.getByRole('button', { name: '実行する' }));

    // **「二重に実行できません」だけでは、いまの結果へ行けない。**
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('実行 SIM-1'),
    );
  });
});

describe('S93 実行結果', () => {
  function runWith(steps: unknown[], finishedAt: string | null = null) {
    return respond(() =>
      new Response(
        JSON.stringify({
          runId: 'SIM-1',
          scenario: 'STANDARD',
          scenarioLabel: '一般貨物の標準輸送',
          status: finishedAt ? 'FAILED' : 'RUNNING',
          statusLabel: finishedAt ? '失敗' : '実行中',
          seed: null,
          startedAt: '2026-09-14T01:00:00Z',
          finishedAt,
          startedBy: 'admin01',
          steps,
        }),
        { status: 200 },
      ),
    );
  }

  it('US34 §1: 工程ごとに成否・所要時間・生成した識別子が出る', async () => {
    runWith([step()]);

    renderRun();

    expect(await screen.findByText('予約の登録')).toBeInTheDocument();
    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getByText('120 ミリ秒')).toBeInTheDocument();
  });

  it('US34 §5: 生成した識別子から業務画面へ行ける', async () => {
    runWith([step()]);

    renderRun();

    await userEvent.click(await screen.findByRole('link', { name: 'BK-1' }));

    expect(await screen.findByText('予約詳細')).toBeInTheDocument();
  });

  it('US34 §2: 止まった工程の理由が出る（それまでの記録も残る）', async () => {
    runWith(
      [
        step(),
        step({
          stepNo: 2,
          kind: 'ASSIGN_ROUTE',
          kindLabel: '経路の確定',
          outcome: 'FAILED',
          outcomeLabel: '失敗',
          producedId: null,
          failureStatus: 422,
          failureMessage: '期限に間に合う経路の候補が 1 件もありません',
        }),
      ],
      '2026-09-14T01:05:00Z',
    );

    renderRun();

    expect(
      await screen.findByText(/期限に間に合う経路の候補が 1 件もありません/),
    ).toBeInTheDocument();
    // **それまでに作られたものは取り消さない**（どこまで進んだかを追える）。
    expect(screen.getByRole('link', { name: 'BK-1' })).toBeInTheDocument();
  });

  it('行き先の無い識別子は素のまま出す（見た目で当てない）', async () => {
    runWith([
      step({ kind: 'REGISTER_SHIPPER', kindLabel: '荷主の登録', producedId: 'SHP-1' }),
    ]);

    renderRun();

    expect(await screen.findByText('SHP-1')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'SHP-1' })).not.toBeInTheDocument();
  });
});
