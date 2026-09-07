import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
    lastNotifiedAt: null,
    returnedToRoutingAt: null,
    returnReason: null,
    // **本物と同じ形にする。** キーが無いと undefined になり、`=== null` の
    // 判定が実装と違う結果になる（モックが本物より厳しい形の失敗）。
    conditionReviewReason: null,
    conditionReviewRequestedAt: null,
    conditionReviewResponse: null,
    conditionReviewRespondedAt: null,
    confirmedAt: null,
    trackingNumber: null,
    trackingIssuedAt: null,
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
    expect(screen.getByText('設定済')).toBeInTheDocument();
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

describe('S22 荷主への通知（US12）', () => {
  /** 予約・旅程・通知履歴を URL で出し分ける。1 つの本体を返すと本物より甘くなる。 */
  function mockApi(over: Record<string, unknown>, notifications: unknown[] = []) {
    return vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes('/notifications') && (init as RequestInit)?.method !== 'POST') {
        return Promise.resolve(
          new Response(JSON.stringify({ items: notifications }), { status: 200 }),
        );
      }
      if (url.includes('/itinerary')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              legs: [{
                legSeq: 1,
                voyageNumber: 'V-1',
                loadUnLocode: 'JPTYO',
                unloadUnLocode: 'USNYC',
                loadAt: '2026-09-10T00:00:00Z',
                unloadAt: '2026-09-24T00:00:00Z',
              }],
            }),
            { status: 200 },
          ),
        );
      }
      if (url.includes('/revisions')) {
        return Promise.resolve(new Response(JSON.stringify({ items: [] }), { status: 200 }));
      }
      return Promise.resolve(new Response(JSON.stringify(booking(over)), { status: 200 }));
    });
  }

  const ROUTED = { bookingStatus: 'ROUTE_PROPOSED', routingStatus: 'ROUTED' };
  /** 通知済み。**lastNotifiedAt が入る**——画面はこれで履歴を問い合わせるか決める。 */
  const NOTIFIED = {
    bookingStatus: 'ROUTE_NOTIFIED',
    routingStatus: 'ROUTED',
    lastNotifiedAt: '2026-09-07T00:00:00Z',
  };

  it('US12 §2: 通知する内容を送る前に確かめられる', async () => {
    // 何を伝えるのかが読めないまま送れると、荷主に何を言ったのか分からなくなる。
    mockApi(ROUTED);

    renderDetail();

    await screen.findByRole('heading', { name: '荷主への通知' });
    // 経由港・所要日数・到着予定日は旅程から作る。料金概算は US21（IT13）が
    // 正典で、いまは出さない（0 円と読まれる）。
    const content = screen.getByLabelText('通知内容') as HTMLTextAreaElement;
    // 内容は旅程が届いてから作る。旅程を待たずに送れると、空の通知が記録される。
    await waitFor(() => expect(content.value).toContain('JPTYO → USNYC'));
    expect(content.value).toContain('所要 14 日');
    expect(content.value).toContain('到着予定');
    expect(content.value).not.toContain('円');
    // 打ち直せると、実際の旅程と違うことを伝えられる。
    expect(content).toHaveAttribute('readonly');
  });

  it('US12 §2: 乗り継ぎのある旅程では通る港が順に並ぶ', async () => {
    // 直行 1 区間だけを通すと、`legs.map(unloadUnLocode)` を
    // `[first.load, last.unload]` に縮めても緑になる（IT6 レビュー 低）。
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes('/notifications') && (init as RequestInit)?.method !== 'POST') {
        return Promise.resolve(new Response(JSON.stringify({ items: [] }), { status: 200 }));
      }
      if (url.includes('/itinerary')) {
        return Promise.resolve(new Response(JSON.stringify({
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
              unloadAt: '2026-09-24T00:00:00Z',
            },
          ],
        }), { status: 200 }));
      }
      if (url.includes('/revisions')) {
        return Promise.resolve(new Response(JSON.stringify({ items: [] }), { status: 200 }));
      }
      return Promise.resolve(new Response(JSON.stringify(booking(ROUTED)), { status: 200 }));
    });

    renderDetail();

    await screen.findByRole('heading', { name: '荷主への通知' });
    const content = screen.getByLabelText('通知内容') as HTMLTextAreaElement;
    await waitFor(() => expect(content.value).toContain('JPTYO → SGSIN → USNYC'));
  });

  it('US12 §3: 宛先と内容を入れて通知できる', async () => {
    const fetchSpy = mockApi(ROUTED);

    renderDetail();

    await screen.findByLabelText('通知先メールアドレス');
    await userEvent.clear(screen.getByLabelText('通知先メールアドレス'));
    await userEvent.type(screen.getByLabelText('通知先メールアドレス'), 'shipper@example.com');
    await userEvent.click(screen.getByRole('button', { name: '通知した記録を残す' }));

    await waitFor(() => {
      expect(fetchSpy.mock.calls.some(([url, init]) =>
        String(url).includes('/notifications')
        && (init as RequestInit)?.method === 'POST')).toBe(true);
    });
  });

  it('US12: 経路が決まっていない予約には通知の導線を出さない', async () => {
    // 集約も断るが、押してから 409 で気づく形にしない。
    mockApi({ bookingStatus: 'ROUTE_PROPOSED', routingStatus: 'ROUTING_REQUESTED' });

    renderDetail();

    await screen.findByRole('heading', { name: '予約 B-2026-0903-0001' });
    expect(screen.queryByRole('heading', { name: '荷主への通知' })).not.toBeInTheDocument();
  });

  it('US12 §4: 通知履歴が新しい順に読める（何を伝えたかが残る）', async () => {
    mockApi(NOTIFIED, [
      {
        notifiedAt: '2026-09-07T00:00:00Z',
        recipientEmail: 'shipper@example.com',
        summary: '2 回目（経由港を変更）',
        notifiedBy: 'sales02',
      },
      {
        notifiedAt: '2026-09-06T00:00:00Z',
        recipientEmail: 'shipper@example.com',
        summary: '1 回目',
        notifiedBy: null,
      },
    ]);

    renderDetail();

    const rows = await screen.findAllByTestId(/^notification-/);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('2 回目（経由港を変更）');
    // 誰が通知したか分からないことは「—」で表す（記録は残っている）。
    expect(rows[1]).toHaveTextContent('—');
  });

  it('US12: 通知した予約は経路設計へ戻せる', async () => {
    const fetchSpy = mockApi(NOTIFIED);

    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '経路設計へ戻す' }));
    await userEvent.type(screen.getByLabelText('戻す理由'), '荷主が経由港の変更を希望');
    await userEvent.click(screen.getByRole('button', { name: '戻すことを確定する' }));

    await waitFor(() => {
      expect(fetchSpy.mock.calls.some(([url]) =>
        String(url).includes('/return-to-routing'))).toBe(true);
    });
  });

  it('US12: 通知していない予約には戻す導線を出さない', async () => {
    mockApi(ROUTED);

    renderDetail();

    await screen.findByRole('heading', { name: '荷主への通知' });
    expect(screen.queryByRole('button', { name: '経路設計へ戻す' })).not.toBeInTheDocument();
  });

  it('US12: 理由が空のままでは戻さない', async () => {
    const fetchSpy = mockApi(NOTIFIED);

    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '経路設計へ戻す' }));
    await userEvent.click(screen.getByRole('button', { name: '戻すことを確定する' }));

    expect(await screen.findByText('戻す理由を入力してください')).toBeInTheDocument();
    expect(fetchSpy.mock.calls.filter(([url]) =>
      String(url).includes('/return-to-routing'))).toHaveLength(0);
  });

  it('US10 §4 の対: 差し戻された予約に、営業が協議の結果を返せる', async () => {
    // **差し戻しは一方向しか無かった**（IT6 レビュー・IT7 引き継ぎ 2）。
    // 営業は荷主と協議を終えても、経路設計者に伝える手段が無かった。
    const fetchSpy = mockApi({
      bookingStatus: 'ROUTE_PROPOSED',
      routingStatus: 'ROUTING_REQUESTED',
      conditionReviewReason: '期限内に着ける便がありません',
      conditionReviewRequestedAt: '2026-09-06T00:00:00Z',
    });

    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '協議の結果を返す' }));
    await userEvent.type(screen.getByLabelText('協議の結果'), '荷主が期限を 1 月末まで延ばすことに同意');
    await userEvent.click(screen.getByRole('button', { name: '経路設計者へ返す' }));

    await waitFor(() => {
      expect(fetchSpy.mock.calls.some(([url]) =>
        String(url).includes('/condition-review/response'))).toBe(true);
    });
  });

  it('US10 §4 の対: 差し戻されていない予約には返す導線を出さない', async () => {
    mockApi(ROUTED);

    renderDetail();

    await screen.findByRole('heading', { name: '荷主への通知' });
    expect(screen.queryByRole('button', { name: '協議の結果を返す' })).not.toBeInTheDocument();
  });

  it('US10 §4 の対: 中身が空のままでは返さない', async () => {
    const fetchSpy = mockApi({
      bookingStatus: 'ROUTE_PROPOSED',
      routingStatus: 'ROUTING_REQUESTED',
      conditionReviewReason: '組めません',
      conditionReviewRequestedAt: '2026-09-06T00:00:00Z',
    });

    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '協議の結果を返す' }));
    await userEvent.click(screen.getByRole('button', { name: '経路設計者へ返す' }));

    expect(await screen.findByText('協議の結果を入力してください')).toBeInTheDocument();
    expect(fetchSpy.mock.calls.filter(([url]) =>
      String(url).includes('/condition-review/response'))).toHaveLength(0);
  });

  it('US13 §2: 通知した予約を確定できる', async () => {
    const fetchSpy = mockApi(NOTIFIED);

    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '予約を確定する' }));

    await waitFor(() => {
      expect(fetchSpy.mock.calls.some(([url]) =>
        String(url).includes('/confirmation'))).toBe(true);
    });
  });

  it('US13: 通知していない予約には確定の導線を出さない', async () => {
    // 押してから断られる導線にしない。判定は集約と同じ遷移表を読む。
    mockApi(ROUTED);

    renderDetail();

    await screen.findByRole('heading', { name: '荷主への通知' });
    expect(screen.queryByRole('button', { name: '予約を確定する' })).not.toBeInTheDocument();
  });

  it('US13: 確定済みの予約は二度と確定できないし、経路設計へも戻せない', async () => {
    // 遷移表に CONFIRMED → CONFIRMED も CONFIRMED → ROUTE_PROPOSED も無い。
    mockApi({ ...NOTIFIED, bookingStatus: 'CONFIRMED',
      confirmedAt: '2026-09-08T00:00:00Z' });

    renderDetail();

    expect(await screen.findByText('確定')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '予約を確定する' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '経路設計へ戻す' })).not.toBeInTheDocument();
  });

  it('US13: 営業以外には確定の操作を出さない', async () => {
    useAuthStore.setState({
      user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' },
    });
    mockApi(NOTIFIED);

    renderDetail();

    await screen.findByText('通知済み');
    expect(screen.queryByRole('button', { name: '予約を確定する' })).not.toBeInTheDocument();
  });

  it('US14 §1: 経路設計者は確定した予約に追跡番号を発行できる', async () => {
    useAuthStore.setState({
      user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' },
    });
    const fetchSpy = mockApi({ ...NOTIFIED, bookingStatus: 'CONFIRMED',
      confirmedAt: '2026-09-08T00:00:00Z' });

    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '追跡番号を発行する' }));

    await waitFor(() => {
      expect(fetchSpy.mock.calls.some(([url]) =>
        String(url).includes('/tracking-number'))).toBe(true);
    });
  });

  it('US14: 営業には発行の操作を出さない（発行は経路設計者の仕事）', async () => {
    mockApi({ ...NOTIFIED, bookingStatus: 'CONFIRMED',
      confirmedAt: '2026-09-08T00:00:00Z' });

    renderDetail();

    await screen.findByText('確定');
    expect(screen.queryByRole('button', { name: '追跡番号を発行する' }))
      .not.toBeInTheDocument();
  });

  it('US14 §2: 発行された追跡番号が予約詳細に出る（二重発行の導線は消える）', async () => {
    useAuthStore.setState({
      user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' },
    });
    mockApi({ ...NOTIFIED, bookingStatus: 'TRACKING_ISSUED',
      confirmedAt: '2026-09-08T00:00:00Z',
      trackingNumber: 'T-2026-000042',
      trackingIssuedAt: '2026-09-08T01:00:00Z' });

    renderDetail();

    expect(await screen.findByText('T-2026-000042')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '追跡番号を発行する' }))
      .not.toBeInTheDocument();
  });

  it('US12: 営業以外には通知の操作を出さない（読むのは全員）', async () => {
    useAuthStore.setState({
      user: { username: 'routing01', roles: ['ROLE_ROUTING'], token: 't' },
    });
    mockApi(NOTIFIED, [
      {
        notifiedAt: '2026-09-06T00:00:00Z',
        recipientEmail: 'shipper@example.com',
        summary: '1 回目',
        notifiedBy: 'sales01',
      },
    ]);

    renderDetail();

    // 履歴は読める（経路設計者も「荷主に何を伝えたか」を知る必要がある）。
    expect(await screen.findByTestId(/^notification-/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '通知した記録を残す' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '経路設計へ戻す' })).not.toBeInTheDocument();
  });
});

describe('S22 旅程は設計し直しでも残る（US10・US12）', () => {
  /** 経路を組んだあと、設計依頼中へ戻った予約。旅程は投影に残っている。 */
  function mockReopened(over: Record<string, unknown>) {
    return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/itinerary')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              legs: [{
                legSeq: 1,
                voyageNumber: 'V-1',
                loadUnLocode: 'JPTYO',
                unloadUnLocode: 'USNYC',
                loadAt: '2026-09-10T00:00:00Z',
                unloadAt: '2026-09-24T00:00:00Z',
              }],
            }),
            { status: 200 },
          ),
        );
      }
      if (url.includes('/notifications') || url.includes('/revisions')) {
        return Promise.resolve(new Response(JSON.stringify({ items: [] }), { status: 200 }));
      }
      return Promise.resolve(new Response(JSON.stringify(booking(over)), { status: 200 }));
    });
  }

  it('経路設計へ戻した予約でも、確定済みの旅程は読める', async () => {
    // **クラスタで踏んで分かった。** 状態で出し分けると、戻した瞬間に旅程が
    // 消える。消えると「何を組み直すのか」が分からない。設計にも「旅程は残る」と
    // 書いてあった（ui_design.md）。書いた保証は赤で固定する。
    mockReopened({ bookingStatus: 'ROUTE_PROPOSED', routingStatus: 'ROUTING_REQUESTED' });

    renderDetail();

    expect(await screen.findByRole('heading', { name: '旅程' })).toBeInTheDocument();
    expect(screen.getByTestId('leg-1')).toHaveTextContent('V-1');
  });

  it('一度も経路を組んでいない予約には旅程を出さない', async () => {
    // 空の表を出すと「経路が決まったのに区間が無い」と読める。
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).includes('/itinerary')) {
        return Promise.resolve(new Response(JSON.stringify({ legs: [] }), { status: 200 }));
      }
      return Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 }));
    });

    renderDetail();

    await screen.findByRole('heading', { name: '予約 B-2026-0903-0001' });
    expect(screen.queryByRole('heading', { name: '旅程' })).not.toBeInTheDocument();
  });
});
