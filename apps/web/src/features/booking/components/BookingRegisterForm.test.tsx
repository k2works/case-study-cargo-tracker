import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { isUlid } from '@/lib/ulid';
import { API_BASE, renderApp } from '@/testing/render';
import { server } from '@/testing/server';
import { BookingRegisterForm } from './BookingRegisterForm';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const COMMAND = `${API_BASE}/command/graphql`;
const QUERY = `${API_BASE}/query/graphql`;
type Vars = Record<string, string | number> & { idempotencyKey: string; bookingId: string };

const shippers = [{ id: 'SHIPPER1', name: '山田 太郎', email: 'taro@example.com', kind: 'INDIVIDUAL' }];
const bookingRow = (version: number) => ({ id: 'X', number: 'BK-000001', shipperId: 'SHIPPER1', status: 'RECEIVED', origin: 'JPTYO', destination: 'NLRTM', deadline: '2027-01-15', version, availableCommands: [] });

/** 荷主の検索と予約の一覧（反映の確認）を返す。`reflectedAfter` 回目の一覧から予約が現れる（結果整合の遅れ）。 */
function queryHandler(bookingId: () => string, reflectedAfter: number | null) {
  let polls = 0;
  return http.post(QUERY, async ({ request }) => {
    const { query } = (await request.json()) as { query: string };
    if (query.includes('shippers(')) return HttpResponse.json({ data: { shippers } });
    polls++;
    const visible = reflectedAfter !== null && polls > reflectedAfter;
    return HttpResponse.json({ data: { bookings: visible ? [{ ...bookingRow(1), id: bookingId() }] : [] } });
  });
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>, overrides: Record<string, string> = {}) {
  await screen.findByRole('option', { name: '山田 太郎' });
  await user.selectOptions(screen.getByLabelText('荷主'), 'SHIPPER1');
  const input = async (label: string, value: string) => {
    const el = screen.getByLabelText(label);
    await user.clear(el);
    await user.type(el, overrides[label] ?? value);
  };
  await input('品名', '精密機器');
  await input('重量（kg）', '120.5');
  await input('長さ（cm）', '100');
  await input('幅（cm）', '80');
  await input('高さ（cm）', '60');
  await input('個数', '3');
  await input('出発地（UN/LOCODE）', 'JPTYO');
  await input('目的地（UN/LOCODE）', 'NLRTM');
  await input('希望引渡日', '2026-12-01');
  await input('希望期限', '2027-01-15');
  await user.click(screen.getByRole('button', { name: '予約を登録' }));
}

describe('SC-26 予約登録（US04）', () => {
  it('US04-AC02: 登録すると、ULID の予約 ID と冪等性キーつきで送り、「受け付けました（予約番号）」を先に示す', async () => {
    const sent: Vars[] = [];
    server.use(
      http.post(COMMAND, async ({ request }) => {
        sent.push(((await request.json()) as { variables: Vars }).variables);
        return HttpResponse.json({ data: { registerBooking: { bookingId: sent[0]!.bookingId, number: 'BK-000001', version: 1 } } });
      }),
      queryHandler(() => sent[0]?.bookingId ?? '', null),
    );
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user);
    expect(await screen.findByRole('status')).toHaveTextContent('受け付けました（予約番号 BK-000001）');
    expect(sent).toHaveLength(1);
    expect(isUlid(sent[0]!.bookingId)).toBe(true);
    expect(sent[0]).toMatchObject({ shipperId: 'SHIPPER1', itemName: '精密機器', weightKg: '120.5', quantity: 3, origin: 'JPTYO', destination: 'NLRTM', preferredHandover: '2026-12-01', deadline: '2027-01-15' });
  });

  it('結果整合: 一覧への反映を版が追いつくまで待ち、反映されたら詳細へのリンクを出す', async () => {
    let id = '';
    server.use(
      http.post(COMMAND, async ({ request }) => {
        id = ((await request.json()) as { variables: Vars }).variables.bookingId;
        return HttpResponse.json({ data: { registerBooking: { bookingId: id, number: 'BK-000001', version: 1 } } });
      }),
      queryHandler(() => id, 2), // 3 回目の確認で一覧に現れる
    );
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user);
    expect(await screen.findByText('一覧への反映を待っています')).toBeInTheDocument();
    const link = await screen.findByRole('link', { name: '予約の詳細を開く' });
    expect(link).toHaveAttribute('href', `/bookings/${id}`);
    expect(screen.queryByText('一覧への反映を待っています')).not.toBeInTheDocument();
  });

  it('上限時間を過ぎても追いつかないときは、エラーにせず「反映待ち」を示し、手動で更新できる', async () => {
    let id = '';
    server.use(
      http.post(COMMAND, async ({ request }) => {
        id = ((await request.json()) as { variables: Vars }).variables.bookingId;
        return HttpResponse.json({ data: { registerBooking: { bookingId: id, number: 'BK-000001', version: 1 } } });
      }),
      queryHandler(() => id, null), // 現れない
    );
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user);
    expect(await screen.findByText(/反映待ちです/, {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('受け付けました（予約番号 BK-000001）'); // 失敗と見せない
    expect(screen.getByRole('button', { name: '更新' })).toBeInTheDocument();
  });

  it('通信エラーでは、再送で同じ予約 ID・同じ冪等性キーを使う（二重に登録しない）', async () => {
    const sent: Vars[] = [];
    let fail = true;
    server.use(
      http.post(COMMAND, async ({ request }) => {
        const v = ((await request.json()) as { variables: Vars }).variables;
        sent.push(v);
        return fail ? HttpResponse.error() : HttpResponse.json({ data: { registerBooking: { bookingId: v.bookingId, number: 'BK-000001', version: 1 } } });
      }),
      queryHandler(() => sent[0]?.bookingId ?? '', null),
    );
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user);
    expect(await screen.findByRole('alert')).toHaveTextContent('通信に失敗しました');
    fail = false;
    await user.click(screen.getByRole('button', { name: '予約を登録' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('受け付けました'));
    expect(sent).toHaveLength(2);
    expect(sent[1]!.bookingId).toBe(sent[0]!.bookingId);
    expect(sent[1]!.idempotencyKey).toBe(sent[0]!.idempotencyKey);
  });

  it('不正な入力は、項目ごとに理由を示す', async () => {
    server.use(
      http.post(COMMAND, () =>
        HttpResponse.json({ data: null, errors: [{ message: 'x', extensions: { code: 'VALIDATION_FAILED', fields: [{ field: 'weightKg', reason: '正の値にする' }, { field: 'destination', reason: '出発地と異なる港にする' }] } }] }),
      ),
      queryHandler(() => '', null),
    );
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user);
    const weight = await screen.findByLabelText('重量（kg）');
    await waitFor(() => expect(weight).toBeInvalid());
    expect(weight).toHaveAccessibleDescription('正の値にする');
    expect(screen.getByLabelText('目的地（UN/LOCODE）')).toHaveAccessibleDescription('出発地と異なる港にする');
  });

  it('サーバーが返す項目名（origin / destination / shipperId / dimensions）が、正しい欄に出る', async () => {
    const fields = [
      { field: 'origin', reason: '登録されていない港' },
      { field: 'destination', reason: 'UN/LOCODE ではない' },
      { field: 'shipperId', reason: '26 文字の ULID ではない' },
      { field: 'dimensions', reason: '各辺は小数 2 桁まで' },
    ];
    server.use(
      http.post(COMMAND, () => HttpResponse.json({ data: null, errors: [{ message: 'x', extensions: { code: 'VALIDATION_FAILED', fields } }] })),
      queryHandler(() => '', null),
    );
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user);
    await waitFor(() => expect(screen.getByLabelText('出発地（UN/LOCODE）')).toHaveAccessibleDescription('登録されていない港'));
    expect(screen.getByLabelText('目的地（UN/LOCODE）')).toHaveAccessibleDescription('UN/LOCODE ではない');
    expect(screen.getByLabelText('荷主')).toHaveAccessibleDescription('26 文字の ULID ではない');
    for (const label of ['長さ（cm）', '幅（cm）', '高さ（cm）']) {
      expect(screen.getByLabelText(label)).toHaveAccessibleDescription('各辺は小数 2 桁まで');
    }
  });

  it('個数が整数でなければ、黙って丸めず、送らずに個数の欄に示す', async () => {
    let sent = 0;
    server.use(http.post(COMMAND, () => { sent++; return HttpResponse.json({ data: null }); }), queryHandler(() => '', null));
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user, { 個数: '2.5' });
    await waitFor(() => expect(screen.getByLabelText('個数')).toHaveAccessibleDescription('1 以上の整数で入力してください'));
    expect(sent).toBe(0);
  });

  it('荷主が存在しない（SHIPPER_NOT_FOUND）ときは、その旨を示す', async () => {
    server.use(
      http.post(COMMAND, () => HttpResponse.json({ data: null, errors: [{ message: 'x', extensions: { code: 'SHIPPER_NOT_FOUND' } }] })),
      queryHandler(() => '', null),
    );
    renderApp(<BookingRegisterForm />);
    const user = userEvent.setup();
    await fillAndSubmit(user);
    expect(within(await screen.findByRole('alert')).getByText(/荷主が見つかりません/)).toBeInTheDocument();
  });

  it('荷主が 1 人もいないときは、先に荷主を登録するよう案内する', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { shippers: [] } })));
    renderApp(<BookingRegisterForm />);
    expect(await screen.findByRole('link', { name: '荷主を登録する' })).toHaveAttribute('href', '/shippers/new');
  });
});
