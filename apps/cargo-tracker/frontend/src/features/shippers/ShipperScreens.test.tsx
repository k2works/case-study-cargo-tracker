import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ShipperListPage } from './ShipperListPage';
import { ShipperRegisterPage } from './ShipperRegisterPage';

function withQuery(ui: React.ReactElement, initial = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initial]}>
        <Routes>
          <Route path="/" element={ui} />
          <Route path="/shippers" element={<h1>荷主一覧</h1>} />
          <Route path="/shippers/new" element={<h1>荷主登録</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function respond(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })));
}

afterEach(() => vi.unstubAllGlobals());

describe('S10 荷主一覧', () => {
  it('登録済みの荷主が出る', async () => {
    respond(200, {
      items: [
        {
          shipperId: 'id-1',
          shipperCode: 'SHP-000001',
          shipperType: 'CORPORATE',
          name: '山田商事',
          email: 'sales@example.com',
          phone: null,
          address: null,
          contractNumber: 'CT-0001',
          discountRate: '0.1000',
        },
      ],
    });

    withQuery(<ShipperListPage />);

    expect(await screen.findByText('SHP-000001')).toBeInTheDocument();
    expect(screen.getByText('山田商事')).toBeInTheDocument();
    expect(screen.getByText('法人')).toBeInTheDocument();
  });

  it('鍵を破棄した荷主は「（削除済み）」と出る（空欄にしない）', async () => {
    respond(200, {
      items: [
        {
          shipperId: 'id-2',
          shipperCode: 'SHP-000002',
          shipperType: 'INDIVIDUAL',
          name: null,
          email: null,
          phone: null,
          address: null,
          contractNumber: null,
          discountRate: null,
        },
      ],
    });

    withQuery(<ShipperListPage />);

    // 空欄だと「入力し忘れ」と区別がつかない。削除されたことが分かる形にする。
    await waitFor(() => expect(screen.getAllByText('（削除済み）')).toHaveLength(2));
  });

  it('反映がまだのときは「反映中」を出す（失敗にしない）', async () => {
    respond(202, { message: '登録を受け付けました。反映までしばらくお待ちください' });

    withQuery(<ShipperListPage />);

    expect(await screen.findByText(/反映までしばらくお待ちください/)).toBeInTheDocument();
  });

  it('登録直後の荷主を、投影が追いつく前でも行として差し込む', async () => {
    // **正典（ui_design.md S11）が「S10 に反映中の行を差し込む」と定めている。**
    // 案内文だけだと、上限で切れた一覧では登録した荷主がどこにも出ず、
    // 営業は「登録できていない」と判断して二重に入力する。
    respond(200, { items: [], total: 219 });

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter
          initialEntries={[{
            pathname: '/',
            state: {
              justRegistered: {
                name: '新規商事',
                email: 'new@example.com',
                shipperType: 'INDIVIDUAL',
              },
            },
          }]}
        >
          <Routes>
            <Route path="/" element={<ShipperListPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const row = await screen.findByRole('row', { name: /新規商事/ });
    expect(row).toHaveTextContent('new@example.com');
    expect(row).toHaveTextContent('反映中');
  });

  it('投影が追いついたら差し込んだ行は重複しない', async () => {
    respond(200, {
      items: [{
        shipperId: 'id-9',
        shipperCode: 'SHP-000219',
        shipperType: 'INDIVIDUAL',
        name: '新規商事',
        email: 'new@example.com',
        phone: null,
        address: null,
        contractNumber: null,
        discountRate: null,
      }],
      total: 219,
    });

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter
          initialEntries={[{
            pathname: '/',
            state: {
              justRegistered: {
                name: '新規商事',
                email: 'new@example.com',
                shipperType: 'INDIVIDUAL',
              },
            },
          }]}
        >
          <Routes>
            <Route path="/" element={<ShipperListPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText('SHP-000219');
    expect(screen.getAllByText('新規商事')).toHaveLength(1);
  });

  it('名前で絞り込める（上限を超えた荷主にたどり着く）', async () => {
    // **クラスタで踏んだ欠陥。** 一覧は荷主コード順で上限があるので、新しく
    // 採った荷主ほど後ろに回り 1 ページ目に出ない。
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 219 }), { status: 200 }));
    vi.stubGlobal('fetch', fetchSpy);

    withQuery(<ShipperListPage />);

    await screen.findByText(/219 件のうち/);
    await userEvent.type(screen.getByLabelText('荷主名で絞り込む'), '山田');

    await vi.waitFor(() =>
      expect(String(fetchSpy.mock.calls.at(-1)?.[0])).toContain('q=%E5%B1%B1%E7%94%B0'));
  });

  it('登録画面への導線がある', async () => {
    respond(200, { items: [] });

    withQuery(<ShipperListPage />);

    expect(screen.getByRole('link', { name: '荷主を登録する' })).toBeInTheDocument();
  });
});

describe('S11 荷主登録', () => {
  it('個人では契約番号を求めない', () => {
    withQuery(<ShipperRegisterPage />);

    expect(screen.queryByLabelText('契約番号')).not.toBeInTheDocument();
  });

  it('法人を選ぶと契約番号と割引率を求める', async () => {
    withQuery(<ShipperRegisterPage />);

    await userEvent.click(screen.getByLabelText('法人'));

    expect(screen.getByLabelText('契約番号')).toBeInTheDocument();
    expect(screen.getByLabelText('割引率（0.0000〜0.3000）')).toBeInTheDocument();
  });

  it('個人に戻すと契約情報の欄が消える', async () => {
    // 出すことだけを確かめると、切り替えても残る実装で緑になる。個人に戻したのに
    // 欄が残ると「個人なのに契約番号を求められる」ことになり、消し忘れた値が
    // そのまま送られる。
    withQuery(<ShipperRegisterPage />);
    await userEvent.click(screen.getByLabelText('法人'));
    expect(screen.getByLabelText('契約番号')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('個人'));

    expect(screen.queryByLabelText('契約番号')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('割引率（0.0000〜0.3000）')).not.toBeInTheDocument();
  });

  it('重複メールは API の理由をそのまま出す', async () => {
    respond(409, {
      code: 'SHIPPER_EMAIL_DUPLICATE',
      message: 'このメールアドレスは既に登録されています: a@example.com',
    });

    withQuery(<ShipperRegisterPage />);
    await userEvent.type(screen.getByLabelText('名称'), '山田商事');
    await userEvent.type(screen.getByLabelText('メールアドレス'), 'a@example.com');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('既に登録されています');
  });

  it('重複のあとにもう一度押すと続行の意思を送る', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ code: 'SHIPPER_EMAIL_DUPLICATE', message: '既に登録されています' }),
          { status: 409 },
        ),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ shipperId: 'x' }), { status: 201 }));
    vi.stubGlobal('fetch', fetchMock);

    withQuery(<ShipperRegisterPage />);
    await userEvent.type(screen.getByLabelText('名称'), '山田商事');
    await userEvent.type(screen.getByLabelText('メールアドレス'), 'c@example.com');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('もう一度');

    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    const secondCall = fetchMock.mock.calls[1];
    const secondBody = JSON.parse(String((secondCall?.[1] as RequestInit).body));
    expect(secondBody.acknowledgedDuplicate).toBe(true);
  });

  it('別のメールアドレスに直したら、確認をやり直す', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'SHIPPER_EMAIL_DUPLICATE', message: '既に登録されています' }),
        { status: 409 },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    withQuery(<ShipperRegisterPage />);
    await userEvent.type(screen.getByLabelText('名称'), '山田商事');
    await userEvent.type(screen.getByLabelText('メールアドレス'), 'first@example.com');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));
    await screen.findByRole('alert');

    // 別の（これも重複する）メールアドレスに直して送る。
    await userEvent.clear(screen.getByLabelText('メールアドレス'));
    await userEvent.type(screen.getByLabelText('メールアドレス'), 'second@example.com');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    const lastBody = JSON.parse(
      String((fetchMock.mock.calls.at(-1)?.[1] as RequestInit).body),
    );
    expect(lastBody.acknowledgedDuplicate)
      .toBe(false);
  });

  it('送信中はボタンを押せない（二重登録を防ぐ）', async () => {
    let release: (value: Response) => void = () => {};
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValue(new Promise<Response>((resolve) => (release = resolve))),
    );

    withQuery(<ShipperRegisterPage />);
    await userEvent.type(screen.getByLabelText('名称'), '山田商事');
    await userEvent.type(screen.getByLabelText('メールアドレス'), 'slow@example.com');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    // 応答が返る前に、もう一度押せない状態になっていること。
    expect(await screen.findByRole('button', { name: '登録中…' })).toBeDisabled();
    release(new Response(JSON.stringify({ shipperId: 'x' }), { status: 201 }));
  });

  it('成功すると一覧へ移る', async () => {
    respond(201, { shipperId: 'new-id' });

    withQuery(<ShipperRegisterPage />);
    await userEvent.type(screen.getByLabelText('名称'), '山田商事');
    await userEvent.type(screen.getByLabelText('メールアドレス'), 'b@example.com');
    await userEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: '荷主一覧' })).toBeInTheDocument(),
    );
  });
});
