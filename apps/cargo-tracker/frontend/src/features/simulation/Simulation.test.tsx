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
    waitedMs: 0,
    producedId: 'BK-1',
    failureStatus: null,
    failureMessage: null,
    occurredAt: '2026-09-14T01:00:01Z',
    ...over,
  };
}

/** 予定の工程 1 件（N4）。 */
function planned(over: Record<string, unknown> = {}) {
  return {
    stepNo: 1,
    kind: 'REGISTER_BOOKING',
    kindLabel: '予約の登録',
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
          <Route path="/tracking/:trackingNumber" element={<p>追跡詳細</p>} />
          <Route path="/invoices/:invoiceId" element={<p>請求詳細</p>} />
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
            plannedSteps: [planned()],
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
  function runWith(steps: unknown[], finishedAt: string | null = null,
      planned2?: unknown[]) {
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
          plannedSteps: planned2 ?? steps.map((each, index) => planned({
            stepNo: (each as { stepNo?: number }).stepNo ?? index + 1,
            kind: (each as { kind?: string }).kind,
            kindLabel: (each as { kindLabel?: string }).kindLabel,
          })),
        }),
        { status: 200 },
      ),
    );
  }

  it('N4: 予定の工程を先に並べ、これからの工程と区別する', async () => {
    // **記録済みだけを出すと、連鎖待ちの 30 秒のあいだ画面が何も変わらない。**
    // 「進んでいるのか固まったのか」が読めず、切り分けが止まる。
    runWith([step()], null, [
      planned(),
      planned({ stepNo: 2, kind: 'REQUEST_ROUTING', kindLabel: '経路設計への引き渡し' }),
    ]);

    renderRun();

    expect(await screen.findByText('予約の登録')).toBeInTheDocument();
    expect(screen.getByText('経路設計への引き渡し')).toBeInTheDocument();
    expect(screen.getByText('これから')).toBeInTheDocument();
    expect(screen.getByText('工程（1 / 2 件）')).toBeInTheDocument();
  });

  it('N8: 連鎖の待ちを所要時間とは別の列で出す（足し合わせない）', async () => {
    runWith([step({ elapsedMs: 120, waitedMs: 8000 })]);

    renderRun();

    // **「13 工程が数ミリ秒ずつ」と読ませない。**
    expect(await screen.findByText('120 ミリ秒')).toBeInTheDocument();
    expect(screen.getByText('8000 ミリ秒')).toBeInTheDocument();
  });

  /**
   * 止まった工程から次に取れる行動への行き先。**数え上げる**——1 件だけ
   * 確かめる形は、次に足した行き先も同じ抜け方をする。
   */
  it.each([
    [
      { kind: 'REGISTER_BOOKING', failureMessage: '「荷主の登録」の結果が読めるようになりませんでした' },
      '退避したイベントを見る',
    ],
    [
      { kind: 'ASSIGN_ROUTE', failureMessage: '経路候補がありません' },
      '航海スケジュールを見る',
    ],
  ])('N5: 止まった工程から次の行動へ行ける（%#）', async (over, label) => {
    runWith([step({ outcome: 'FAILED', outcomeLabel: '失敗', producedId: null, ...over })],
      '2026-09-14T01:05:00Z');

    renderRun();

    expect(await screen.findByRole('link', { name: label })).toBeInTheDocument();
  });

  it('N5: 行き先の分からない失敗に、当てずっぽうのリンクを出さない', async () => {
    runWith([step({
      outcome: 'FAILED', outcomeLabel: '失敗', producedId: null,
      kind: 'REGISTER_SHIPPER', failureMessage: '想定していない断り',
    })], '2026-09-14T01:05:00Z');

    renderRun();

    await screen.findByText('想定していない断り');
    expect(screen.queryByRole('link', { name: /見る$/ })).not.toBeInTheDocument();
  });

  it('N6: 同じシナリオをもう一度流せる（一覧へ戻って選び直さない）', async () => {
    const calls: string[] = [];
    respond((url, init) => {
      if (url.endsWith('/simulation/runs') && init?.method === 'POST') {
        calls.push(String(init.body));
        return new Response(JSON.stringify({ runId: 'SIM-2' }), { status: 201 });
      }
      return new Response(
        JSON.stringify({
          runId: 'SIM-1', scenario: 'STANDARD', scenarioLabel: '一般貨物の標準輸送',
          status: 'FAILED', statusLabel: '失敗', seed: null,
          startedAt: '2026-09-14T01:00:00Z', finishedAt: '2026-09-14T01:05:00Z',
          startedBy: 'admin01', steps: [step()], plannedSteps: [planned()],
        }),
        { status: 200 },
      );
    });

    renderRun();
    await screen.findByText('予約の登録');
    await userEvent.click(screen.getByRole('button', { name: '同じシナリオをもう一度流す' }));

    // **切り分けは「直す → 流す」を何度も回す作業である。**
    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toContain('STANDARD');
  });

  it('US34 §1: 工程ごとに成否・所要時間・生成した識別子が出る', async () => {
    runWith([step()]);

    renderRun();

    expect(await screen.findByText('予約の登録')).toBeInTheDocument();
    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getByText('120 ミリ秒')).toBeInTheDocument();
  });

  // **行き先は 1 つずつ数え上げる。** 1 件だけ確かめる形は、次に行き先を
  // 足したときも同じ抜け方をする（実際に 3 件のうち 2 件が、引数を読まない
  // 一覧へ飛んでいた。IT16 のレビュー 高）。
  it.each([
    ['REGISTER_BOOKING', '予約の登録', 'BK-1', '予約詳細'],
    ['ISSUE_TRACKING_NUMBER', '追跡番号の発行', 'TRK-1', '追跡詳細'],
    ['CALCULATE_INVOICE', '料金の算出', 'INV-1', '請求詳細'],
  ])('US34 §5: %s が生成したものから業務画面へ行ける', async (kind, kindLabel, id, heading) => {
    runWith([step({ kind, kindLabel, producedId: id })]);

    renderRun();

    await userEvent.click(await screen.findByRole('link', { name: id }));

    expect(await screen.findByText(heading)).toBeInTheDocument();
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
