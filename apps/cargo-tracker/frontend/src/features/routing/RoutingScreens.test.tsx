import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VoyageListPage } from './VoyageListPage';
import { VoyageRegisterPage } from './VoyageRegisterPage';
import { RoutingWorklistPage } from './RoutingWorklistPage';
import { useAuthStore } from '@/shared/auth/authStore';

function voyage(over: Record<string, unknown> = {}) {
  return {
    voyageNumber: 'V-MOL-001',
    carrierCode: 'MOL',
    carrierName: '商船三井',
    vesselName: 'MOL EXPRESS',
    departureUnLocode: 'JPTYO',
    arrivalUnLocode: 'USNYC',
    departureAt: '2026-09-10T09:00:00Z',
    arrivalAt: '2026-09-24T18:00:00Z',
    cancelled: false,
    acceptedCargoTypes: ['GENERAL'],
    movements: [
      {
        movementSeq: 1,
        departureUnLocode: 'JPTYO',
        arrivalUnLocode: 'USNYC',
        departureAt: '2026-09-10T09:00:00Z',
        arrivalAt: '2026-09-24T18:00:00Z',
      },
    ],
    ...over,
  };
}

function booking(over: Record<string, unknown> = {}) {
  return {
    bookingId: 'b-1',
    bookingNumber: 'B-2026-0903-0001',
    shipperId: 's-1',
    shipperName: '山田商事',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    arrivalDeadline: '2026-12-01',
    cargoType: 'HAZARDOUS',
    weightKg: '1200.00',
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    quantity: 10,
    productName: '塗料',
    hazardImoClass: '3',
    hazardUnNumber: 'UN1263',
    temperatureMinC: null,
    temperatureMaxC: null,
    bookingStatus: 'ROUTE_PROPOSED',
    routingStatus: 'ROUTING_REQUESTED',
    bookedAt: '2026-09-03T01:00:00Z',
    ...over,
  };
}

function renderAt(path: string, element: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={path} element={element} />
          <Route path="/voyages" element={<h1>航海スケジュール一覧</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S32 航海スケジュール一覧', () => {
  it('航海番号・船名・対応貨物を利用者の言葉で出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [voyage()], total: 1 }), { status: 200 }),
    );

    renderAt('/voyages-list', <VoyageListPage />);

    expect(await screen.findByText('V-MOL-001')).toBeInTheDocument();
    expect(screen.getByText('MOL EXPRESS')).toBeInTheDocument();
    // 列挙名のまま見せない（ui_design.md の画面共通の規約）。
    expect(screen.getByText('一般貨物')).toBeInTheDocument();
    expect(screen.queryByText('GENERAL')).not.toBeInTheDocument();
  });

  it('既定では出港済み・キャンセルを外して問い合わせる', async () => {
    // 既定の絞りが消えると、出港してしまった便が混ざって一覧全体が信用されなくなる。
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 }),
    );

    renderAt('/voyages-list', <VoyageListPage />);

    await waitFor(() => expect(fetchSpy).toHaveBeenCalled());
    expect(String(fetchSpy.mock.calls[0]?.[0])).toContain('includeFinished=false');
  });

  it('0 件でも「取得できなかった」と読めないようにする', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 }),
    );

    renderAt('/voyages-list', <VoyageListPage />);

    expect(await screen.findByText('航海はありません')).toBeInTheDocument();
  });
});

describe('S33 航海スケジュール登録', () => {
  it('寄港地を順序つきで送り、対応貨物種別も載せる', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ voyageNumber: 'V-MOL-001' }), { status: 201 }),
    );

    renderAt('/voyages/new', <VoyageRegisterPage />);

    await userEvent.type(screen.getByLabelText('航海番号'), 'V-MOL-001');
    await userEvent.type(screen.getByLabelText('運送会社コード'), 'MOL');
    await userEvent.type(screen.getByLabelText('運送会社'), '商船三井');
    await userEvent.type(screen.getByLabelText('船名'), 'MOL EXPRESS');
    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('到着地'), 'USNYC');
    await userEvent.type(screen.getByLabelText('出発日時'), '2026-09-10T09:00');
    await userEvent.type(screen.getByLabelText('到着日時'), '2026-09-24T18:00');
    await userEvent.click(screen.getByLabelText('危険物'));
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => expect(fetchSpy).toHaveBeenCalled());
    const body = JSON.parse(String(fetchSpy.mock.calls.at(-1)?.[1]?.body));
    expect(body.vesselName).toBe('MOL EXPRESS');
    expect(body.movements).toHaveLength(1);
    expect(body.movements[0].departureUnLocode).toBe('JPTYO');
    // 既定で一般貨物が入っている。選び忘れるとその航海が候補から消える。
    expect(body.acceptedCargoTypes).toEqual(['GENERAL', 'HAZARDOUS']);
  });

  it('寄港地を増やせる', async () => {
    renderAt('/voyages/new', <VoyageRegisterPage />);

    await userEvent.click(screen.getByRole('button', { name: '寄港地を追加する' }));

    expect(screen.getAllByLabelText('出発地')).toHaveLength(2);
  });

  it('同じ航海番号は問いかけとして出す（500 に見せない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'ILLEGAL_STATE', message: '航海 V-MOL-001 は既に登録されています' }),
        { status: 409 },
      ),
    );

    renderAt('/voyages/new', <VoyageRegisterPage />);
    await userEvent.type(screen.getByLabelText('航海番号'), 'V-MOL-001');
    await userEvent.type(screen.getByLabelText('運送会社コード'), 'MOL');
    await userEvent.type(screen.getByLabelText('運送会社'), '商船三井');
    await userEvent.type(screen.getByLabelText('船名'), 'MOL EXPRESS');
    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('到着地'), 'USNYC');
    await userEvent.type(screen.getByLabelText('出発日時'), '2026-09-10T09:00');
    await userEvent.type(screen.getByLabelText('到着日時'), '2026-09-24T18:00');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('既に登録されています');
    expect(screen.getByRole('alert')).toHaveTextContent('番号を直してください');
  });
});

describe('S30 経路設計作業一覧', () => {
  it('予約（bookingms）から供給する。routingms には問い合わせない', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [booking()], total: 1 }), { status: 200 }),
    );

    renderAt('/routing/worklist', <RoutingWorklistPage />);

    expect(await screen.findByText('B-2026-0903-0001')).toBeInTheDocument();
    const url = String(fetchSpy.mock.calls[0]?.[0]);
    expect(url).toContain('/booking/bookings/routing-worklist');
    expect(url).not.toContain('/routing/');
  });

  it('誤配は状態の欄でそれと分かる', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ items: [booking({ routingStatus: 'MISROUTED' })], total: 1 }),
        { status: 200 },
      ),
    );

    renderAt('/routing/worklist', <RoutingWorklistPage />);

    expect(await screen.findByText('誤配')).toBeInTheDocument();
  });

  it('航海スケジュール一覧へ行ける', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 }),
    );

    renderAt('/routing/worklist', <RoutingWorklistPage />);

    expect(
      await screen.findByRole('link', { name: '航海スケジュール一覧へ' }),
    ).toBeInTheDocument();
  });
});
