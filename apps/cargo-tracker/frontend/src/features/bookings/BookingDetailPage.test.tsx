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
