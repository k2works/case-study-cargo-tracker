import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BookingListPage } from './BookingListPage';
import { BookingRegisterPage } from './BookingRegisterPage';
import { useAuthStore } from '@/shared/auth/authStore';

function booking(over: Record<string, unknown> = {}) {
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
    lengthCm: '120.00',
    widthCm: '80.00',
    heightCm: '100.00',
    quantity: 10,
    productName: '自動車部品',
    hazardImoClass: null,
    hazardUnNumber: null,
    temperatureMinC: null,
    temperatureMaxC: null,
    bookingStatus: 'PRELIMINARY',
    routingStatus: 'NOT_ROUTED',
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
          <Route path="/bookings" element={<h1>予約一覧</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S20 予約一覧', () => {
  it('予約番号と状態を利用者の言葉で出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [booking()], total: 1 }), { status: 200 }),
    );

    renderAt('/bookings-list', <BookingListPage />);

    expect(await screen.findByText('B-2026-0903-0001')).toBeInTheDocument();
    // 列挙名のまま見せない（ui_design.md の画面共通の規約）。
    expect(screen.getByText('仮受付')).toBeInTheDocument();
    expect(screen.queryByText('PRELIMINARY')).not.toBeInTheDocument();
    expect(screen.getByText('自動車部品（一般）')).toBeInTheDocument();
    expect(screen.queryByText('GENERAL')).not.toBeInTheDocument();
  });

  it('0 件は失敗と区別して伝える', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 }),
    );

    renderAt('/bookings-list', <BookingListPage />);

    expect(await screen.findByText(/予約はありません/)).toBeInTheDocument();
  });

  it('終了したものも表示すると問い合わせが変わる', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ items: [], total: 0 }), { status: 200 }));

    renderAt('/bookings-list', <BookingListPage />);
    await screen.findByText(/予約はありません/);

    await userEvent.click(screen.getByLabelText('終了したものも表示'));

    // 既定で外しているのは、終わった予約が混ざると一覧全体が信用されなくなるため。
    await waitFor(() => {
      const urls = fetchMock.mock.calls.map((c) => String(c[0]));
      expect(urls.some((u) => u.includes('includeFinished=true'))).toBe(true);
    });
  });
});

describe('S21 予約登録', () => {
  /** 荷主の一覧と、そのあとの登録の応答を順に返す。 */
  function mockShippersThen(response: Response) {
    return vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            items: [
              {
                shipperId: 's-1',
                shipperCode: 'SHP-000001',
                shipperType: 'INDIVIDUAL',
                name: '山田商事',
                email: 'a@example.com',
                phone: null,
                address: null,
                contractNumber: null,
                discountRate: null,
              },
            ],
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValue(response);
  }

  it('荷主は一覧から選ぶ（識別子を打たせない）', async () => {
    // 識別子を打たせると、営業は一覧を開いて UUID を書き写すことになる。
    mockShippersThen(new Response('{}', { status: 200 }));

    renderAt('/bookings-new', <BookingRegisterPage />);

    const select = await screen.findByLabelText('荷主');
    expect(select.tagName).toBe('SELECT');
    expect(await screen.findByRole('option', { name: '山田商事（SHP-000001）' }))
      .toBeInTheDocument();
  });

  it('危険物を選んだときだけ IMO クラスを出す', async () => {
    renderAt('/bookings-new', <BookingRegisterPage />);

    expect(screen.queryByLabelText('IMO クラス')).not.toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('危険物'));

    // 常に出すと「一般貨物なのに危険物申告を求められる」ことになる。
    expect(screen.getByLabelText('IMO クラス')).toBeInTheDocument();
    expect(screen.queryByLabelText('温度条件（下限 ℃）')).not.toBeInTheDocument();
  });

  it('冷凍を選んだときだけ温度条件を出す', async () => {
    renderAt('/bookings-new', <BookingRegisterPage />);

    await userEvent.click(screen.getByLabelText('冷凍・冷蔵'));

    expect(screen.getByLabelText('温度条件（下限 ℃）')).toBeInTheDocument();
    expect(screen.queryByLabelText('IMO クラス')).not.toBeInTheDocument();
  });

  it('寸法を送る', async () => {
    const fetchMock = mockShippersThen(
      new Response(JSON.stringify({ bookingId: 'b-1' }), { status: 201 }),
    );

    renderAt('/bookings-new', <BookingRegisterPage />);
    // 一覧が届いてから選ぶ。届く前に選ぶと、選択肢が空の select を触ることになる。
    await screen.findByRole('option', { name: '山田商事（SHP-000001）' });
    await userEvent.selectOptions(screen.getByLabelText('荷主'), 's-1');
    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('目的地'), 'USNYC');
    await userEvent.type(screen.getByLabelText('到着期限'), '2026-12-01');
    await userEvent.type(screen.getByLabelText('重量 (kg)'), '1200');
    await userEvent.type(screen.getByLabelText('長さ (cm)'), '120');
    await userEvent.type(screen.getByLabelText('幅 (cm)'), '80');
    await userEvent.type(screen.getByLabelText('高さ (cm)'), '100');
    await userEvent.type(screen.getByLabelText('数量'), '10');
    await userEvent.type(screen.getByLabelText('品名'), '自動車部品');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    // 集約が持つ値を画面が落とすと、US04 §受入基準 2 を満たせない。
    const post = fetchMock.mock.calls.find((c) => c[1]?.method === 'POST');
    const body = JSON.parse(String(post?.[1]?.body));
    expect(body.lengthCm).toBe('120');
    expect(body.widthCm).toBe('80');
    expect(body.heightCm).toBe('100');
  });

  it('業務規則で断られたら理由を出す', async () => {
    mockShippersThen(
      new Response(
        JSON.stringify({ code: 'BUSINESS_RULE_VIOLATION', message: '出発地と目的地が同じです: JPTYO' }),
        { status: 422 },
      ),
    );

    renderAt('/bookings-new', <BookingRegisterPage />);
    // 一覧が届いてから選ぶ。届く前に選ぶと、選択肢が空の select を触ることになる。
    await screen.findByRole('option', { name: '山田商事（SHP-000001）' });
    await userEvent.selectOptions(screen.getByLabelText('荷主'), 's-1');
    await userEvent.type(screen.getByLabelText('出発地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('目的地'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('到着期限'), '2026-12-01');
    await userEvent.type(screen.getByLabelText('重量 (kg)'), '1200');
    await userEvent.type(screen.getByLabelText('長さ (cm)'), '120');
    await userEvent.type(screen.getByLabelText('幅 (cm)'), '80');
    await userEvent.type(screen.getByLabelText('高さ (cm)'), '100');
    await userEvent.type(screen.getByLabelText('数量'), '10');
    await userEvent.type(screen.getByLabelText('品名'), '自動車部品');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    // 断ったのは集約の判断であって画面の誤りではない。理由をそのまま見せる。
    expect(await screen.findByRole('alert')).toHaveTextContent('出発地と目的地が同じです');
  });
});
