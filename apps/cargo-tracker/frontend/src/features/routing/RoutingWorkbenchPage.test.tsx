import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RoutingWorkbenchPage } from './RoutingWorkbenchPage';
import { useAuthStore } from '@/shared/auth/authStore';

function booking() {
  return {
    bookingId: 'b-1',
    bookingNumber: 'B-2026-0903-0001',
    shipperId: 's-1',
    shipperName: '山田商事',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    arrivalDeadline: '2026-12-01',
    cargoType: 'GENERAL',
    weightKg: '1200.00',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    quantity: 10,
    productName: '自動車部品',
    hazardImoClass: null,
    hazardUnNumber: null,
    temperatureMinC: null,
    temperatureMaxC: null,
    bookingStatus: 'ROUTE_PROPOSED',
    routingStatus: 'ROUTING_REQUESTED',
    bookedAt: '2026-09-03T01:00:00Z',
    routingRequestedAt: '2026-09-04T01:00:00Z',
    updatedAt: null,
    updatedBy: null,
  };
}

function leg(voyageNumber: string, from: string, to: string, load: string, unload: string) {
  return {
    voyageNumber,
    loadUnLocode: from,
    unloadUnLocode: to,
    loadTime: load,
    unloadTime: unload,
  };
}

function mockApi(candidatesResponse: Response) {
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    if (String(input).includes('/route-candidates')) {
      return Promise.resolve(candidatesResponse);
    }
    return Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 }));
  });
}

function renderWorkbench() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/routing/bookings/b-1']}>
        <Routes>
          <Route path="/routing/bookings/:bookingId" element={<RoutingWorkbenchPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S31 経路設計ワークベンチ', () => {
  it('候補が推奨順に出て、航海番号と経由港が読める', async () => {
    mockApi(
      new Response(
        JSON.stringify({
          candidates: [
            {
              legs: [leg('V-MOL-001', 'JPTYO', 'USNYC', '2026-09-10T09:00:00Z',
                '2026-09-24T18:00:00Z')],
              transitDays: 14,
              direct: true,
            },
            {
              legs: [
                leg('V-1', 'JPTYO', 'SGSIN', '2026-09-10T09:00:00Z', '2026-09-16T08:00:00Z'),
                leg('V-2', 'SGSIN', 'USNYC', '2026-09-17T06:00:00Z', '2026-09-25T18:00:00Z'),
              ],
              transitDays: 15,
              direct: false,
            },
          ],
          truncated: false,
        }),
        { status: 200 },
      ),
    );

    renderWorkbench();

    const first = await screen.findByTestId('candidate-1');
    expect(first).toHaveTextContent('直行便');
    expect(first).toHaveTextContent('V-MOL-001');
    expect(first).toHaveTextContent('14 日');
    const second = screen.getByTestId('candidate-2');
    // 経由港が読めないと、どこで積み替えるのか分からない。
    expect(second).toHaveTextContent('SGSIN');
    expect(second).toHaveTextContent('V-1 → V-2');
  });

  it('候補 0 件は「見つからなかった」と条件調整の案内を出す（エラーにしない）', async () => {
    mockApi(
      new Response(JSON.stringify({ candidates: [], truncated: false }), { status: 200 }),
    );

    renderWorkbench();

    expect(await screen.findByText(/期限内に到着できる経路が見つかりませんでした/))
      .toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('探せなかったときは「候補が無い」と言わない（503）', async () => {
    // 空の候補一覧に見せると、経路設計者は直らない条件を変え続けることになる。
    mockApi(
      new Response(JSON.stringify({ code: 'ROUTE_SEARCH_UNAVAILABLE', message: 'x' }),
        { status: 503 }),
    );

    renderWorkbench();

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('経路設計サービスに問い合わせできませんでした');
    expect(screen.queryByText(/経路が見つかりませんでした/)).not.toBeInTheDocument();
  });

  it('0 件で打ち切りに当たったら「条件を変えても増えない」と伝える', async () => {
    // 期限を延ばす・港を広げるという逆の案内を重ねて出さない
    // （IT5 レビュー 高 1）。乗り継ぎの上限で捨てた枝は条件では戻らない。
    mockApi(
      new Response(JSON.stringify({ candidates: [], truncated: true }), { status: 200 }),
    );

    renderWorkbench();

    expect(await screen.findByText(/条件を変えても候補は増えません/)).toBeInTheDocument();
    expect(screen.queryByText(/到着期限を延ばすか/)).not.toBeInTheDocument();
  });

  it('候補が出ていて打ち切りに当たったら「上限まで探した」と伝える', async () => {
    mockApi(
      new Response(
        JSON.stringify({
          candidates: [
            {
              legs: [leg('V-MOL-001', 'JPTYO', 'USNYC', '2026-09-10T09:00:00Z',
                '2026-09-24T18:00:00Z')],
              transitDays: 14,
              direct: true,
            },
          ],
          truncated: true,
        }),
        { status: 200 },
      ),
    );

    renderWorkbench();

    expect(await screen.findByText(/上限まで探しました/)).toBeInTheDocument();
    expect(screen.queryByText(/条件を変えても候補は増えません/)).not.toBeInTheDocument();
  });

  it('経路が確定済みの予約では確定ボタンを出さない（押してから断らせない）', async () => {
    // S30 の「設計済みも表示」から開いたときに起きる。押すと集約が断るが、
    // その文言（経路設計を依頼していない…）は状況の説明として的外れ。
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).includes('/route-candidates')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              candidates: [
                {
                  legs: [leg('V-MOL-001', 'JPTYO', 'USNYC', '2026-09-10T09:00:00Z',
                    '2026-09-24T18:00:00Z')],
                  transitDays: 14,
                  direct: true,
                },
              ],
              truncated: false,
            }),
            { status: 200 },
          ),
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify({ ...booking(), routingStatus: 'ROUTED' }), { status: 200 }),
      );
    });

    renderWorkbench();

    expect(await screen.findByText(/この予約は経路が確定しています/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'この経路で確定' })).not.toBeInTheDocument();
  });

  it('費用は出さず、どこで出るかを書く', async () => {
    // 0 円と出すと「費用 0 円の経路」と読める（US08 §受入基準 3 の未達）。
    mockApi(
      new Response(JSON.stringify({ candidates: [], truncated: false }), { status: 200 }),
    );

    renderWorkbench();

    expect(await screen.findByText(/費用はこの画面では出ません/)).toBeInTheDocument();
  });
});

describe('S31 経路の確定（US09）', () => {
  function mockWithCandidates() {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes('/route-candidates')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              candidates: [
                {
                  legs: [
                    leg('V-MOL-001', 'JPTYO', 'USNYC', '2026-09-10T09:00:00Z',
                      '2026-09-24T18:00:00Z'),
                  ],
                  transitDays: 14,
                  direct: true,
                },
              ],
              truncated: false,
            }),
            { status: 200 },
          ),
        );
      }
      if (url.endsWith('/route') && init?.method === 'POST') {
        return Promise.resolve(new Response(JSON.stringify({ bookingId: 'b-1' }), { status: 200 }));
      }
      return Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 }));
    });
    return fetchSpy;
  }

  it('候補を選んで確定すると、旅程そのものを送る', async () => {
    const fetchSpy = mockWithCandidates();

    renderWorkbench();

    await userEvent.click(await screen.findByRole('radio', { name: '候補 1' }));
    await userEvent.click(screen.getByRole('button', { name: 'この経路で確定' }));

    await waitFor(() => {
      const call = fetchSpy.mock.calls.find(
        ([url, init]) => String(url).endsWith('/route') && init?.method === 'POST',
      );
      expect(call).toBeDefined();
      // 候補 ID ではなく区間の列を送る。候補はテーブルに持たないので、
      // 送るまでの間に航海が更新されうる。
      const body = JSON.parse(String(call?.[1]?.body));
      expect(body.legs).toHaveLength(1);
      expect(body.legs[0].voyageNumber).toBe('V-MOL-001');
      expect(body.legs[0].loadUnLocode).toBe('JPTYO');
    });
  });

  it('選ばずに確定しようとしても送らない', async () => {
    const fetchSpy = mockWithCandidates();

    renderWorkbench();

    await userEvent.click(await screen.findByRole('button', { name: 'この経路で確定' }));

    expect(await screen.findByText('経路候補を選んでください')).toBeInTheDocument();
    expect(
      fetchSpy.mock.calls.filter(([url]) => String(url).endsWith('/route')),
    ).toHaveLength(0);
  });

  it('確定できなかったときは理由を出す（409）', async () => {
    // 集約が断った理由（期限を満たさないなど）を出さないと、押せない理由が分からない。
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes('/route-candidates')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              candidates: [
                {
                  legs: [
                    leg('V-MOL-001', 'JPTYO', 'USNYC', '2026-09-10T09:00:00Z',
                      '2026-09-24T18:00:00Z'),
                  ],
                  transitDays: 14,
                  direct: true,
                },
              ],
              truncated: false,
            }),
            { status: 200 },
          ),
        );
      }
      if (url.endsWith('/route') && init?.method === 'POST') {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              code: 'BUSINESS_RULE_VIOLATION',
              message: '選んだ旅程は予約の経路仕様を満たしません',
            }),
            { status: 422 },
          ),
        );
      }
      return Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 }));
    });

    renderWorkbench();

    await userEvent.click(await screen.findByRole('radio', { name: '候補 1' }));
    await userEvent.click(screen.getByRole('button', { name: 'この経路で確定' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('選んだ旅程は予約の経路仕様を満たしません');
  });
});
