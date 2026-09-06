import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VoyageListPage } from './VoyageListPage';
import { VoyageRegisterPage } from './VoyageRegisterPage';
import { RoutingWorklistPage } from './RoutingWorklistPage';
import { VoyageDetailPage } from './VoyageDetailPage';
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
    routingRequestedAt: '2026-09-03T02:30:00Z',
    ...over,
  };
}

function renderAt(path: string, element: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          {/* 検索条件はクエリ文字列で持つので、経路はパスだけで組む。 */}
          <Route path={path.split('?')[0] as string} element={element} />
          <Route path="/voyages" element={<h1>航海スケジュール一覧</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** 航海番号をパスに持つ画面（S34 / S33 更新）。実際の経路の形で描く。 */
function renderVoyage(path: string, element: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/voyages/:voyageNumber" element={element} />
          <Route path="/voyages/:voyageNumber/edit" element={element} />
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
    expect(within(screen.getByRole('table')).getByText('一般貨物')).toBeInTheDocument();
    expect(screen.queryByText('GENERAL')).not.toBeInTheDocument();
  });

  it('出港済みが混ざったとき、状態の欄でそれと分かる', async () => {
    // 「出港済み・キャンセルも表示」を選んでも状態が「予定」のままだと、
    // 混ざっているのに見分けられない。
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [
            voyage({ voyageNumber: 'V-PAST-1', departureAt: '2020-01-01T00:00:00Z' }),
            voyage({ voyageNumber: 'V-CANCEL', cancelled: true }),
          ],
          total: 2,
        }),
        { status: 200 },
      ),
    );

    renderAt('/voyages-list', <VoyageListPage />);

    expect(await screen.findByText('出港済み')).toBeInTheDocument();
    expect(screen.getByText('キャンセル')).toBeInTheDocument();
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

describe('S32 航海スケジュールの検索', () => {
  it('条件を入れて絞り込むと、その条件で問い合わせる', async () => {
    // Response の本体は 1 度しか読めない。絞り込みで 2 度目の問い合わせが
    // 走るので、毎回作り直す。
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ items: [voyage()], total: 1 }), { status: 200 }),
      ),
    );

    renderAt('/voyages-list', <VoyageListPage />);
    await screen.findByText('V-MOL-001');

    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('目的地'), 'USNYC');
    await userEvent.type(screen.getByLabelText('出発日（開始）'), '2026-09-01');
    await userEvent.type(screen.getByLabelText('出発日（終了）'), '2026-09-30');
    await userEvent.selectOptions(screen.getByLabelText('対応貨物種別'), 'HAZARDOUS');
    await userEvent.click(screen.getByRole('button', { name: '絞り込む' }));

    await waitFor(() => {
      const url = String(fetchSpy.mock.calls.at(-1)?.[0]);
      expect(url).toContain('departure=JPTYO');
      expect(url).toContain('arrival=USNYC');
      expect(url).toContain('cargoType=HAZARDOUS');
      // 期間は日付で入れて絶対時刻で送る。終了日はその日の終わりまで含める。
      // **日付は業務タイムゾーンの一日である。** UTC の一日で送ると、日本時間の
      // 朝 9 時より前に出る航海が前日の指定で拾われる。
      expect(url).toContain('departFrom=2026-08-31T15%3A00%3A00Z');
      expect(url).toContain('departTo=2026-09-30T14%3A59%3A59Z');
    });
  });

  it('条件に合う航海が無いときは、条件を外して探し直せる', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 })),
    );

    renderAt('/voyages-list', <VoyageListPage />);
    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.click(screen.getByRole('button', { name: '絞り込む' }));

    expect(await screen.findByText('条件に合う航海はありません')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '条件を消して探し直す' }));

    await waitFor(() => expect(screen.getByLabelText('出発地')).toHaveValue(''));
    expect(await screen.findByText('航海はありません')).toBeInTheDocument();
  });

  it('入力の誤りは断りとして出す（0 件に見せない）', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            code: 'BUSINESS_RULE_VIOLATION',
            message: 'UN/LOCODE は英大文字 5 文字です: jptyo',
          }),
          { status: 422 },
        ),
      ),
    );

    renderAt('/voyages-list', <VoyageListPage />);
    await userEvent.type(screen.getByLabelText('出発地'), 'jptyo');
    await userEvent.click(screen.getByRole('button', { name: '絞り込む' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('UN/LOCODE');
  });

  it('経路設計作業一覧から来たときは貨物種別を引き継ぐ', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 })),
    );

    renderAt(
      '/voyages-list?cargoType=HAZARDOUS&departure=JPTYO&arrival=USNYC&departTo=2026-12-01',
      <VoyageListPage />,
    );

    await waitFor(() => {
      const url = String(fetchSpy.mock.calls.at(-1)?.[0]);
      expect(url).toContain('cargoType=HAZARDOUS');
      expect(url).toContain('departure=JPTYO');
    });
    expect(screen.getByLabelText('対応貨物種別')).toHaveValue('HAZARDOUS');
    expect(screen.getByLabelText('出発地')).toHaveValue('JPTYO');
    expect(screen.getByLabelText('目的地')).toHaveValue('USNYC');
    expect(screen.getByLabelText('出発日（終了）')).toHaveValue('2026-12-01');
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
    await userEvent.type(screen.getByLabelText('運送会社名'), '商船三井');
    await userEvent.type(screen.getByLabelText('船名'), 'MOL EXPRESS');
    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('到着地'), 'USNYC');
    await userEvent.type(screen.getByLabelText('出発日時（日本時間）'), '2026-09-10T09:00');
    await userEvent.type(screen.getByLabelText('到着日時（日本時間）'), '2026-09-24T18:00');
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
    await userEvent.type(screen.getByLabelText('運送会社名'), '商船三井');
    await userEvent.type(screen.getByLabelText('船名'), 'MOL EXPRESS');
    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('到着地'), 'USNYC');
    await userEvent.type(screen.getByLabelText('出発日時（日本時間）'), '2026-09-10T09:00');
    await userEvent.type(screen.getByLabelText('到着日時（日本時間）'), '2026-09-24T18:00');
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

  it('いつ引き渡されたかが行から読める', async () => {
    // 一覧は到着期限が近い順。期限が遠い案件は下に沈むので、引き渡しから
    // どれだけ経ったかが読めないと放置に気づけない（IT3 レビュー R.4）。
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [booking()], total: 1 }), { status: 200 }),
    );

    renderAt('/routing/worklist', <RoutingWorklistPage />);

    expect(await screen.findByRole('columnheader', { name: '引き渡し' })).toBeInTheDocument();
    expect(screen.getByText('2026/09/03 11:30')).toBeInTheDocument();
  });

  it('引き渡し前の予約は引き渡し日時を空欄にする', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ items: [booking({ routingRequestedAt: null })], total: 1 }),
        { status: 200 },
      ),
    );

    renderAt('/routing/worklist', <RoutingWorklistPage />);

    expect(await screen.findByText('B-2026-0903-0001')).toBeInTheDocument();
    expect(screen.getByTestId('routing-requested-at-b-1')).toHaveTextContent('—');
  });

  it('危険物の予約から、その種別を引き継いで航海を探せる', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ items: [booking()], total: 1 }), { status: 200 })),
    );

    renderAt('/routing/worklist', <RoutingWorklistPage />);

    // 種別だけでは足りない。行に出ている出発地・目的地・到着期限も引き継がないと、
    // 経路設計者は毎回 JPTYO / USNYC を打ち直し、打ち間違えれば 0 件を
    // 「航海が無い」と読む。
    const link = await screen.findByRole('link', { name: '対応する航海を探す' });
    const href = link.getAttribute('href') ?? '';
    expect(href).toContain('cargoType=HAZARDOUS');
    expect(href).toContain('departure=JPTYO');
    expect(href).toContain('arrival=USNYC');
    expect(href).toContain('departTo=2026-12-01');
  });

  it('冷凍の予約は航海側の呼び名に翻訳して引き継ぐ', async () => {
    // Booking は REFRIGERATED、Routing は REEFER。写さずに渡すと
    // 「知らない貨物種別です」で断られる。
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({ items: [booking({ cargoType: 'REFRIGERATED' })], total: 1 }),
          { status: 200 },
        ),
      ),
    );

    renderAt('/routing/worklist', <RoutingWorklistPage />);

    const link = await screen.findByRole('link', { name: '対応する航海を探す' });
    expect(link.getAttribute('href') ?? '').toContain('cargoType=REEFER');
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

/**
 * S34 の fetch モック。<b>航海と影響範囲の 2 本を URL で出し分ける。</b>
 *
 * <p>1 つの本体を全部の問い合わせに返すと、影響範囲（{@code by-voyage}）の応答が
 * 航海の形になって画面が壊れる。本物より甘いモックにすると、本物が返さない形で
 * 検査が通ってしまう。</p>
 */
function respondVoyage(body: unknown, status = 200, affected: unknown[] = []) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) =>
    Promise.resolve(
      String(input).includes('/by-voyage/')
        ? new Response(JSON.stringify({ items: affected }), { status: 200 })
        : new Response(JSON.stringify(body), { status }),
    ),
  );
}

describe('S34 航海詳細', () => {
  it('寄港地が順に読める', async () => {
    // IT3 では登録した中身を確認する画面が無く、409 の案内が指す先も無かった。
    respondVoyage(voyage({
            movements: [
              {
                movementSeq: 1,
                departureUnLocode: 'JPTYO',
                arrivalUnLocode: 'SGSIN',
                departureAt: '2026-09-10T09:00:00Z',
                arrivalAt: '2026-09-16T08:00:00Z',
              },
              {
                movementSeq: 2,
                departureUnLocode: 'SGSIN',
                arrivalUnLocode: 'USNYC',
                departureAt: '2026-09-17T06:00:00Z',
                arrivalAt: '2026-09-24T18:00:00Z',
              },
            ],
          }));

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByRole('heading', { name: /V-MOL-001/ })).toBeInTheDocument();
    const rows = await screen.findAllByTestId(/^movement-/);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('JPTYO → SGSIN');
    expect(rows[1]).toHaveTextContent('SGSIN → USNYC');
  });

  it('対応貨物種別と最終更新が読める', async () => {
    respondVoyage(voyage({
            acceptedCargoTypes: ['GENERAL', 'HAZARDOUS'],
            updatedAt: '2026-09-05T02:00:00Z',
            updatedBy: 'routing02',
          }));

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByText(/一般貨物 \/ 危険物/)).toBeInTheDocument();
    expect(screen.getByText(/routing02/)).toBeInTheDocument();
  });

  it('一度も更新していなければ最終更新は出さない', async () => {
    respondVoyage(voyage());

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByRole('heading', { name: /V-MOL-001/ })).toBeInTheDocument();
    expect(screen.queryByText('最終更新')).not.toBeInTheDocument();
  });

  it('更新画面へ行ける', async () => {
    respondVoyage(voyage());

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByRole('link', { name: '更新する' })).toHaveAttribute(
      'href',
      '/voyages/V-MOL-001/edit',
    );
  });

  it('キャンセル済みの航海には更新の導線を出さない', async () => {
    // 集約が断る（不変条件 5）。画面に出しておくと、押してから 409 で気づく。
    respondVoyage(voyage({ cancelled: true }));

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByText('キャンセル済み')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '更新する' })).not.toBeInTheDocument();
  });

  const AFFECTED = [
    {
      bookingId: 'b-1',
      bookingNumber: 'B-2026-0903-0001',
      bookingStatus: 'ROUTE_PROPOSED',
      routingStatus: 'ROUTED',
    },
    {
      bookingId: 'b-2',
      bookingNumber: 'B-2026-0903-0002',
      bookingStatus: 'ROUTE_NOTIFIED',
      routingStatus: 'ROUTED',
    },
  ];

  it('H.2: 止める前に、巻き込む予約の件数と行き先が読める（US24）', async () => {
    // 止めても予約側の旅程は自動では戻らない。件数だけ出しても、そこへ行けなければ
    // 仕事は進まない（マニュアル 07 章は「控えてください」と書いていた）。
    respondVoyage(voyage(), 200, AFFECTED);

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByText(/この航海で経路を組んだ予約が 2 件あります/))
      .toBeInTheDocument();
    // 行き先が無ければ、件数は不安を増やすだけ。
    expect(await screen.findByRole('link', { name: 'B-2026-0903-0001' }))
      .toHaveAttribute('href', '/bookings/b-1');
    // 状態は列挙名で見せない。
    expect(screen.getByText('通知済み')).toBeInTheDocument();
    expect(screen.queryByText('ROUTE_NOTIFIED')).not.toBeInTheDocument();
  });

  it('H.2: 巻き込む予約が無ければ、そのことを言う（黙らない）', async () => {
    // 黙ると「まだ読み込んでいない」と区別が付かず、止めてよいのか分からない。
    respondVoyage(voyage(), 200, []);

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByText(/この航海で経路を組んだ予約はありません/))
      .toBeInTheDocument();
  });

  it('H.2: キャンセル済みの航海でも巻き込んだ予約は読める', async () => {
    // 止めたあとこそ「誰を組み直すか」を読む場面。止めたら消える形にしない。
    respondVoyage(voyage({ cancelled: true }), 200, [AFFECTED[0]]);

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByText(/この航海で経路を組んだ予約が 1 件あります/))
      .toBeInTheDocument();
  });

  it('R.1: 理由を入れてキャンセルできる', async () => {
    const fetchSpy = respondVoyage(voyage());

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'この航海をキャンセルする' }));
    await userEvent.type(screen.getByLabelText('キャンセル理由'), '運航中止');
    await userEvent.click(screen.getByRole('button', { name: 'キャンセルを確定する' }));

    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith(
        expect.stringContaining('/routing/voyages/V-MOL-001/cancel'),
        expect.objectContaining({ method: 'POST' }),
      );
    });
  });

  it('R.1: 理由が空のままでは送らない', async () => {
    // 集約も断るが、押してから 400 で気づく形にしない。
    const fetchSpy = respondVoyage(voyage());

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'この航海をキャンセルする' }));
    await userEvent.click(screen.getByRole('button', { name: 'キャンセルを確定する' }));

    expect(await screen.findByText('キャンセル理由を入力してください')).toBeInTheDocument();
    expect(
      fetchSpy.mock.calls.filter(([url]) => String(url).includes('/cancel')),
    ).toHaveLength(0);
  });

  it('R.1: キャンセル済みには止める導線も理由も出す', async () => {
    respondVoyage(voyage({
            cancelled: true,
            cancelledAt: '2026-09-05T02:00:00Z',
            cancelReason: '運航中止',
            cancelledBy: 'routing02',
          }));

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    // 止めた理由を記録しても、読み口が無ければ誰にも見えない。
    expect(await screen.findByText(/運航中止/)).toBeInTheDocument();
    expect(screen.getByText(/routing02/)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'この航海をキャンセルする' }),
    ).not.toBeInTheDocument();
  });

  it('投影がまだなら「反映中」と伝える（見つかりませんにしない）', async () => {
    respondVoyage(
      { voyageNumber: 'V-MOL-001', message: '反映までしばらくお待ちください' },
      202,
    );

    renderVoyage('/voyages/V-MOL-001', <VoyageDetailPage />);

    expect(await screen.findByText(/反映まで/)).toBeInTheDocument();
  });
});

describe('S33 航海スケジュール更新', () => {
  it('既登録の内容が入った状態で開く', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(voyage()), { status: 200 }),
    );

    renderVoyage('/voyages/V-MOL-001/edit', <VoyageRegisterPage />);

    await waitFor(() =>
      expect(screen.getByLabelText('船名')).toHaveValue('MOL EXPRESS'),
    );
    // 航海番号は変えられない（不変条件 1）。直せる欄として出すと、
    // 別の航海を作ったつもりで既存を壊す操作に見える。
    expect(screen.queryByLabelText('航海番号')).not.toBeInTheDocument();
    expect(screen.getByText('V-MOL-001')).toBeInTheDocument();
  });

  it('読み込み直しても、入力中の値を巻き戻さない', async () => {
    // 既登録の値を入れるのは 1 度だけ。問い合わせが返るたびに入れ直すと、
    // 打っている途中の値が既登録の値へ戻る（クラスタ E2E で実際に起きた）。
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(voyage()), { status: 200 })),
    );

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/voyages/V-MOL-001/edit']}>
          <Routes>
            <Route path="/voyages/:voyageNumber/edit" element={<VoyageRegisterPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByLabelText('船名')).toHaveValue('MOL EXPRESS'));
    await userEvent.clear(screen.getByLabelText('船名'));
    await userEvent.type(screen.getByLabelText('船名'), 'MOL VOYAGER');

    // 画面を離れず、同じ問い合わせが返り直した状況を作る。
    await client.refetchQueries({ queryKey: ['voyage', 'V-MOL-001'] });

    await waitFor(() => expect(screen.getByLabelText('船名')).toHaveValue('MOL VOYAGER'));
  });

  it('差分を確かめてから更新する', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes('/diff')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              voyageNumber: 'V-MOL-001',
              changes: [{ label: '船名', before: 'MOL EXPRESS', after: 'MOL VOYAGER' }],
            }),
            { status: 200 },
          ),
        );
      }
      if (init?.method === 'PUT') {
        return Promise.resolve(
          new Response(JSON.stringify({ voyageNumber: 'V-MOL-001' }), { status: 200 }),
        );
      }
      return Promise.resolve(new Response(JSON.stringify(voyage()), { status: 200 }));
    });

    renderVoyage('/voyages/V-MOL-001/edit', <VoyageRegisterPage />);

    await waitFor(() => expect(screen.getByLabelText('船名')).toHaveValue('MOL EXPRESS'));
    await userEvent.clear(screen.getByLabelText('船名'));
    await userEvent.type(screen.getByLabelText('船名'), 'MOL VOYAGER');
    await userEvent.click(screen.getByRole('button', { name: '差分を確認する' }));

    expect(await screen.findByText('MOL EXPRESS → MOL VOYAGER')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '更新する' }));

    await waitFor(() =>
      expect(
        fetchSpy.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'PUT'),
      ).toBe(true),
    );
  });

  it('差分を出したあとに入力を変えたら、確認をやり直させる', async () => {
    // 確認した内容と違うものが送られると、差分の確認が形だけになる。
    // 運航変更の入力はもう一桁直す操作が普通に起きる。
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/diff')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              voyageNumber: 'V-MOL-001',
              changes: [{ label: '船名', before: 'MOL EXPRESS', after: 'MOL VOYAGER' }],
            }),
            { status: 200 },
          ),
        );
      }
      return Promise.resolve(new Response(JSON.stringify(voyage()), { status: 200 }));
    });

    renderVoyage('/voyages/V-MOL-001/edit', <VoyageRegisterPage />);
    await waitFor(() => expect(screen.getByLabelText('船名')).toHaveValue('MOL EXPRESS'));
    await userEvent.clear(screen.getByLabelText('船名'));
    await userEvent.type(screen.getByLabelText('船名'), 'MOL VOYAGER');
    await userEvent.click(screen.getByRole('button', { name: '差分を確認する' }));
    await screen.findByText('MOL EXPRESS → MOL VOYAGER');

    await userEvent.type(screen.getByLabelText('船名'), ' II');

    expect(screen.queryByRole('button', { name: '更新する' })).not.toBeInTheDocument();
    expect(screen.queryByText('MOL EXPRESS → MOL VOYAGER')).not.toBeInTheDocument();
  });

  it('「キャンセル」を選ぶと更新を送らない', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/diff')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              voyageNumber: 'V-MOL-001',
              changes: [{ label: '船名', before: 'MOL EXPRESS', after: 'MOL VOYAGER' }],
            }),
            { status: 200 },
          ),
        );
      }
      return Promise.resolve(new Response(JSON.stringify(voyage()), { status: 200 }));
    });

    renderVoyage('/voyages/V-MOL-001/edit', <VoyageRegisterPage />);

    await waitFor(() => expect(screen.getByLabelText('船名')).toHaveValue('MOL EXPRESS'));
    await userEvent.clear(screen.getByLabelText('船名'));
    await userEvent.type(screen.getByLabelText('船名'), 'MOL VOYAGER');
    await userEvent.click(screen.getByRole('button', { name: '差分を確認する' }));
    await screen.findByText('MOL EXPRESS → MOL VOYAGER');

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(
      fetchSpy.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'PUT'),
    ).toBe(false);
  });

  it('変えていなければ更新を送らせない', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/diff')) {
        return Promise.resolve(
          new Response(JSON.stringify({ voyageNumber: 'V-MOL-001', changes: [] }), {
            status: 200,
          }),
        );
      }
      return Promise.resolve(new Response(JSON.stringify(voyage()), { status: 200 }));
    });

    renderVoyage('/voyages/V-MOL-001/edit', <VoyageRegisterPage />);

    await waitFor(() => expect(screen.getByLabelText('船名')).toHaveValue('MOL EXPRESS'));
    await userEvent.click(screen.getByRole('button', { name: '差分を確認する' }));

    expect(await screen.findByText('変更はありません')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '更新する' })).not.toBeInTheDocument();
  });
});
