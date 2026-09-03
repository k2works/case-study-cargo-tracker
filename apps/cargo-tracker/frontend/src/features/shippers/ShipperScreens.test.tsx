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
