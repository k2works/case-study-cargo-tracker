import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BookingDetailPage } from './BookingDetailPage';
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
    routingRequestedAt: null,
    updatedAt: null,
    updatedBy: null,
    ...over,
  };
}

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/bookings/b-1']}>
        <Routes>
          <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
          <Route path="/bookings/:bookingId/edit" element={<h1>予約を修正する</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S22 予約詳細', () => {
  it('状態・輸送条件・貨物を利用者の言葉で出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking()), { status: 200 }),
    );

    renderDetail();

    expect(await screen.findByRole('heading', { name: '予約 B-2026-0903-0001' }))
      .toBeInTheDocument();
    expect(screen.getByText('仮受付')).toBeInTheDocument();
    expect(screen.getByText('山田商事')).toBeInTheDocument();
    expect(screen.getByText('120.00 × 80.00 × 100.00 cm')).toBeInTheDocument();
    expect(screen.queryByText('PRELIMINARY')).not.toBeInTheDocument();
  });

  it('危険物と冷凍の付帯情報は、あるときだけ出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify(booking({
          cargoType: 'HAZARDOUS',
          hazardImoClass: '3',
          hazardUnNumber: 'UN1263',
        })),
        { status: 200 },
      ),
    );

    renderDetail();

    expect(await screen.findByText('IMO 3 / UN1263')).toBeInTheDocument();
    // 一般貨物の予約に空の温度条件を出すと、入力し忘れと区別が付かない。
    expect(screen.queryByText('温度条件')).not.toBeInTheDocument();
  });

  it('温度条件を持つ予約では温度条件を出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify(booking({
          cargoType: 'REFRIGERATED',
          temperatureMinC: '-20.00',
          temperatureMaxC: '-10.00',
        })),
        { status: 200 },
      ),
    );

    renderDetail();

    expect(await screen.findByText('-20.00 〜 -10.00 ℃')).toBeInTheDocument();
  });

  it('寸法が無い予約では「—」を出す（空欄にしない）', async () => {
    // 空欄だと「入力し忘れ」と区別が付かない。
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify(booking({ lengthCm: null, widthCm: null, heightCm: null })),
        { status: 200 },
      ),
    );

    renderDetail();

    await screen.findByRole('heading', { name: /予約 B-/ });
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('鍵を破棄した荷主は「（削除済み）」と出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking({ shipperName: null })), { status: 200 }),
    );

    renderDetail();

    expect(await screen.findByText('（削除済み）')).toBeInTheDocument();
  });

  it('投影がまだなら「反映中」を出す（失敗にしない）', async () => {
    // 404 にすると「登録に失敗した」と読めてしまう。
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ bookingId: 'b-1', message: '反映までしばらくお待ちください' }), {
        status: 202,
      }),
    );

    renderDetail();

    expect(await screen.findByText(/反映までしばらくお待ちください/)).toBeInTheDocument();
  });

  it('取得に失敗したら理由を出す', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 'E', message: 'x' }), { status: 500 }),
    );

    renderDetail();

    expect(await screen.findByRole('alert')).toHaveTextContent('予約を取得できませんでした');
  });

  it('営業以外には「経路設計を依頼する」を出さない', async () => {
    // 引き渡すのは営業の仕事。詳細画面は経路設計・追跡にも開いているので、
    // 状態だけで出し分けると、見に来ただけの人が引き渡せる。
    useAuthStore.setState({
      user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' },
    });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking()), { status: 200 }),
    );

    renderDetail();
    await screen.findByText('仮受付');

    expect(
      screen.queryByRole('button', { name: '経路設計を依頼する' }),
    ).not.toBeInTheDocument();
  });

  it('押すと状態が「経路提案中」に変わる（US06 の成功経路）', async () => {
    // 成功経路をクラスタ E2E だけに頼らない。クラスタが無い回でも守られる形にする。
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    fetchSpy.mockResolvedValueOnce(
      new Response(JSON.stringify(booking()), { status: 200 }),
    );
    fetchSpy.mockResolvedValueOnce(
      new Response(JSON.stringify({ bookingId: 'b-1' }), { status: 202 }),
    );
    fetchSpy.mockResolvedValue(
      new Response(
        JSON.stringify(booking({ bookingStatus: 'ROUTE_PROPOSED' })),
        { status: 200 },
      ),
    );

    renderDetail();
    (await screen.findByRole('button', { name: '経路設計を依頼する' })).click();

    expect(await screen.findByText('経路提案中')).toBeInTheDocument();
  });

  it('仮受付の予約には「経路設計を依頼する」が出る（US06）', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking()), { status: 200 }),
    );

    renderDetail();

    expect(
      await screen.findByRole('button', { name: '経路設計を依頼する' }),
    ).toBeInTheDocument();
  });

  it('精算済の予約には「経路設計を依頼する」が出ない（デモ項目 6）', async () => {
    // 出し分けは集約と同じ遷移表の述語を通す。ここで status を直に見ると、
    // 遷移表が変わったときに画面だけが古い判断のまま残る。
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking({ bookingStatus: 'SETTLED' })), { status: 200 }),
    );

    renderDetail();

    expect(await screen.findByText('精算済')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '経路設計を依頼する' }),
    ).not.toBeInTheDocument();
  });

  it('引き渡しが断られたら理由を出す（500 に見せない）', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    fetchSpy.mockResolvedValueOnce(new Response(JSON.stringify(booking()), { status: 200 }));
    fetchSpy.mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'ILLEGAL_STATE', message: '状態 SETTLED の予約は引き渡せません' }),
        { status: 409 },
      ),
    );

    renderDetail();
    (await screen.findByRole('button', { name: '経路設計を依頼する' })).click();

    expect(await screen.findByRole('alert')).toHaveTextContent('引き渡せません');
  });
});

describe('S22 から S24 への導線（US32）', () => {
  it('仮受付の予約は営業が修正できる', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 })),
    );

    renderDetail();

    expect(await screen.findByRole('link', { name: '修正する' })).toHaveAttribute(
      'href',
      '/bookings/b-1/edit',
    );
  });

  it('経路提案中の予約には修正の導線を出さない', async () => {
    // 集約が断る（US32 §受入基準 1）。出しておくと、押してから 409 で気づく。
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify(booking({ bookingStatus: 'ROUTE_PROPOSED' })), {
          status: 200,
        }),
      ),
    );

    renderDetail();

    await screen.findByText('経路提案中');
    expect(screen.queryByRole('link', { name: '修正する' })).not.toBeInTheDocument();
  });

  it('営業以外には修正の導線を出さない', async () => {
    useAuthStore.setState({
      user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' },
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 })),
    );

    renderDetail();

    await screen.findByText('仮受付');
    expect(screen.queryByRole('link', { name: '修正する' })).not.toBeInTheDocument();
  });

  it('修正した予約は「いつ・誰が」が読める', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify(booking({ updatedAt: '2026-09-05T02:00:00Z', updatedBy: 'sales02' })),
          { status: 200 },
        ),
      ),
    );

    renderDetail();

    expect(await screen.findByText(/sales02/)).toBeInTheDocument();
  });

  it('一度も修正していない予約に最終更新は出さない', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 })),
    );

    renderDetail();

    await screen.findByText('仮受付');
    expect(screen.queryByText('最終更新')).not.toBeInTheDocument();
  });
});

describe('S22 修正履歴（US32 §受入基準 4）', () => {
  function mockBookingAnd(revisions: unknown[]) {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/revisions')) {
        return Promise.resolve(new Response(JSON.stringify({ items: revisions }), { status: 200 }));
      }
      return Promise.resolve(
        new Response(
          JSON.stringify(booking({ updatedAt: '2026-09-05T00:00:00Z', updatedBy: 'sales02' })),
          { status: 200 },
        ),
      );
    });
  }

  it('R.2: 何を変えたかが読める', async () => {
    mockBookingAnd([
      {
        updatedAt: '2026-09-05T00:00:00Z',
        updatedBy: 'sales02',
        label: '目的地',
        before: 'USNYC',
        after: 'GBLON',
      },
      {
        updatedAt: '2026-09-05T00:00:00Z',
        updatedBy: 'sales02',
        label: '品名',
        before: '自動車部品',
        after: '塗料',
      },
    ]);

    renderDetail();

    expect(await screen.findByRole('heading', { name: '修正履歴' })).toBeInTheDocument();
    const row = await screen.findByTestId('revision-目的地');
    expect(row).toHaveTextContent('USNYC');
    expect(row).toHaveTextContent('GBLON');
    expect(screen.getByTestId('revision-品名')).toHaveTextContent('塗料');
  });

  it('R.2: 最終更新の項目が欠けている応答でも修正履歴を出さない', async () => {
    // `!== null` で見ると undefined が「直した」になり、一度も直していない予約に
    // 空の修正履歴が出る（マニュアルのキャプチャで実測）。
    // 応答から最終更新の項目そのものを落とす（null ではなく「無い」状態）。
    const withoutUpdate = Object.fromEntries(
      Object.entries(booking()).filter(([key]) => key !== 'updatedAt' && key !== 'updatedBy'),
    );
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).includes('/revisions')) {
        return Promise.resolve(
          new Response(JSON.stringify({ items: [{ label: '品名' }] }), { status: 200 }),
        );
      }
      return Promise.resolve(new Response(JSON.stringify(withoutUpdate), { status: 200 }));
    });

    renderDetail();

    // **表示の有無で見ない。** 問い合わせが返る前にアサートすると、条件が壊れて
    // いても「まだ出ていない」だけで緑になる（実測: 条件を戻しても赤にならなかった）。
    // 直していない予約には**問い合わせ自体を出さない**ことを見る。
    expect(await screen.findByRole('heading', { name: '貨物' })).toBeInTheDocument();
    await new Promise((resolve) => {
      setTimeout(resolve, 50);
    });
    expect(
      fetchSpy.mock.calls.filter(([url]) => String(url).includes('/revisions')),
    ).toHaveLength(0);
  });

  it('R.2: 一度も直していなければ修正履歴を出さない', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking()), { status: 200 }),
    );

    renderDetail();

    expect(await screen.findByRole('heading', { name: '状態' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '修正履歴' })).not.toBeInTheDocument();
  });
});

describe('S22 旅程（US09）', () => {
  it('経路が決まっていれば区間が積む順に読める', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/itinerary')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              legs: [
                {
                  legSeq: 1,
                  voyageNumber: 'V-1',
                  loadUnLocode: 'JPTYO',
                  unloadUnLocode: 'SGSIN',
                  loadAt: '2026-09-10T00:00:00Z',
                  unloadAt: '2026-09-16T00:00:00Z',
                },
                {
                  legSeq: 2,
                  voyageNumber: 'V-2',
                  loadUnLocode: 'SGSIN',
                  unloadUnLocode: 'USNYC',
                  loadAt: '2026-09-17T00:00:00Z',
                  unloadAt: '2026-09-25T00:00:00Z',
                },
              ],
            }),
            { status: 200 },
          ),
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify(booking({ routingStatus: 'ROUTED' })), { status: 200 }),
      );
    });

    renderDetail();

    expect(await screen.findByRole('heading', { name: '旅程' })).toBeInTheDocument();
    expect(screen.getByTestId('leg-1')).toHaveTextContent('JPTYO');
    expect(screen.getByTestId('leg-1')).toHaveTextContent('SGSIN');
    expect(screen.getByTestId('leg-2')).toHaveTextContent('V-2');
  });

  it('経路設定状態が予約の状態とは別に読める', async () => {
    // 予約の状態は「仮受付」のままでも、経路は先に決まる。片方だけ出すと
    // 予約詳細から経路の進み具合が読めない。
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify(booking({ bookingStatus: 'PRELIMINARY', routingStatus: 'ROUTED' })),
        { status: 200 },
      ),
    );

    renderDetail();

    expect(await screen.findByRole('heading', { name: '状態' })).toBeInTheDocument();
    expect(screen.getByText('経路設定状態')).toBeInTheDocument();
    expect(screen.getByText('設計済')).toBeInTheDocument();
    expect(screen.getByText('仮受付')).toBeInTheDocument();
  });

  it('経路が決まっていなければ旅程を出さない', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking()), { status: 200 }),
    );

    renderDetail();

    expect(await screen.findByRole('heading', { name: '状態' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '旅程' })).not.toBeInTheDocument();
  });
});
