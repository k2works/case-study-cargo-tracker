import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { API_BASE, renderApp } from '@/testing/render';
import { server } from '@/testing/server';
import { ShipperRegisterForm } from './ShipperRegisterForm';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

type Vars = { idempotencyKey: string; name: string; address: string; contact: string; email: string; acknowledgedDuplicate?: boolean };
const COMMAND = `${API_BASE}/command/graphql`;
const ok = (id = 'SHIPPER1') => HttpResponse.json({ data: { registerShipper: { shipperId: id } } });
const QUERY = `${API_BASE}/query/graphql`;
const existing = () => http.post(QUERY, () => HttpResponse.json({ data: { shipper: { id: 'EXISTING1', kind: 'INDIVIDUAL', name: '既存 太郎', address: '大阪府大阪市', contact: '06-0000-0000', email: 'taro@example.com' } } }));
const biz = (code: string, extra: object = {}) => HttpResponse.json({ data: null, errors: [{ message: code, extensions: { code, ...extra } }] });

async function fill(user: ReturnType<typeof userEvent.setup>, v: Partial<Vars> = {}) {
  await user.type(screen.getByLabelText('氏名または社名'), v.name ?? '山田 太郎');
  await user.type(screen.getByLabelText('住所'), v.address ?? '東京都千代田区');
  await user.type(screen.getByLabelText('連絡先'), v.contact ?? '03-1234-5678');
  await user.type(screen.getByLabelText('メールアドレス'), v.email ?? 'taro@example.com');
}

describe('SC-21 荷主登録（US02）', () => {
  it('US02-AC01: 入力して登録すると、冪等性キーつきで送り、成功したら荷主 ID を通知する', async () => {
    const sent: Vars[] = [];
    server.use(http.post(COMMAND, async ({ request }) => { sent.push(((await request.json()) as { variables: Vars }).variables); return ok(); }));
    const onRegistered = vi.fn();
    renderApp(<ShipperRegisterForm onRegistered={onRegistered} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    await waitFor(() => expect(onRegistered).toHaveBeenCalledWith('SHIPPER1'));
    expect(sent).toHaveLength(1);
    expect(sent[0]).toMatchObject({ name: '山田 太郎', email: 'taro@example.com', acknowledgedDuplicate: false });
    expect(sent[0]!.idempotencyKey.length).toBeGreaterThan(10);
  });

  it('送信中は押せない（二重押下で二重に送らない）', async () => {
    let calls = 0;
    server.use(http.post(COMMAND, async () => { calls++; await new Promise((r) => setTimeout(r, 80)); return ok(); }));
    renderApp(<ShipperRegisterForm onRegistered={vi.fn()} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user);
    const button = screen.getByRole('button', { name: '荷主を登録' });
    await user.click(button);
    await user.click(button);
    await waitFor(() => expect(button).toBeEnabled());
    expect(calls).toBe(1);
  });

  it('通信エラーは入力を残して再送の案内を出し、再送では同じ冪等性キーを使う', async () => {
    const keys: string[] = [];
    let fail = true;
    server.use(http.post(COMMAND, async ({ request }) => {
      keys.push(((await request.json()) as { variables: Vars }).variables.idempotencyKey);
      return fail ? HttpResponse.error() : ok();
    }));
    const onRegistered = vi.fn();
    renderApp(<ShipperRegisterForm onRegistered={onRegistered} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('通信に失敗しました');
    expect(screen.getByLabelText('氏名または社名')).toHaveValue('山田 太郎');
    fail = false;
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    await waitFor(() => expect(onRegistered).toHaveBeenCalled());
    expect(keys).toHaveLength(2);
    expect(keys[0]).toBe(keys[1]);
  });

  it('不正な入力は、項目ごとに理由を示す（どの項目がなぜ誤りか）', async () => {
    server.use(http.post(COMMAND, () => biz('VALIDATION_FAILED', { fields: [{ field: 'name', reason: '空にできない' }, { field: 'email', reason: 'メールアドレスの形式ではない' }] })));
    renderApp(<ShipperRegisterForm onRegistered={vi.fn()} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user, { email: 'bad' });
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    expect(await screen.findByText('メールアドレスの形式ではない')).toBeInTheDocument();
    expect(screen.getByLabelText('メールアドレス')).toBeInvalid();
    expect(screen.getByLabelText('メールアドレス')).toHaveAccessibleDescription('メールアドレスの形式ではない');
  });

  it('US02-AC02: 同じメールの荷主がいると警告し、「既存の荷主を使う」か「登録を続ける」を選ばせる', async () => {
    server.use(http.post(COMMAND, () => biz('DUPLICATE_EMAIL', { existingShipperId: 'EXISTING1' })), existing());
    const onUseExisting = vi.fn();
    renderApp(<ShipperRegisterForm onRegistered={vi.fn()} onUseExisting={onUseExisting} />);
    const user = userEvent.setup();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('同じメールアドレスの荷主が既に登録されています');
    // 既存の荷主を示す（同じ人か、同じ会社の別担当者かを見分けるため。AC-02「既存の荷主が表示され」）
    expect(await screen.findByText('既存 太郎')).toBeInTheDocument();
    expect(screen.getByText('大阪府大阪市')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '既存の荷主を使う' }));
    expect(onUseExisting).toHaveBeenCalledWith('EXISTING1');
  });

  it('既存の荷主の ID が分からないときは、「既存の荷主を使う」を出さない（空の ID で移動させない）', async () => {
    server.use(http.post(COMMAND, () => biz('DUPLICATE_EMAIL')));
    renderApp(<ShipperRegisterForm onRegistered={vi.fn()} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    expect(await screen.findByRole('button', { name: '登録を続ける' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '既存の荷主を使う' })).not.toBeInTheDocument();
  });

  it('「登録を続ける」が通信エラーになっても、再送では同じキーを使う（二重に登録しない）', async () => {
    const sent: Vars[] = [];
    server.use(
      existing(),
      http.post(COMMAND, async ({ request }) => {
        const v = ((await request.json()) as { variables: Vars }).variables;
        sent.push(v);
        if (!v.acknowledgedDuplicate) return biz('DUPLICATE_EMAIL', { existingShipperId: 'EXISTING1' });
        return sent.length === 2 ? HttpResponse.error() : ok('SHIPPER2');
      }),
    );
    const onRegistered = vi.fn();
    renderApp(<ShipperRegisterForm onRegistered={onRegistered} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    await user.click(await screen.findByRole('button', { name: '登録を続ける' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('通信に失敗しました');
    // やり直し：もう一度登録 → 重複の警告 → 登録を続ける
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    await user.click(await screen.findByRole('button', { name: '登録を続ける' }));
    await waitFor(() => expect(onRegistered).toHaveBeenCalledWith('SHIPPER2'));
    expect(sent).toHaveLength(4);
    expect(sent[3]!.acknowledgedDuplicate).toBe(true);
    expect(sent[3]!.idempotencyKey).toBe(sent[1]!.idempotencyKey);
    expect(sent[2]!.idempotencyKey).toBe(sent[0]!.idempotencyKey);
    expect(sent[1]!.idempotencyKey).not.toBe(sent[0]!.idempotencyKey);
  });

  it('US02-AC02: 「登録を続ける」は、重複を承知した新しい要求（新しい冪等性キー）として送る', async () => {
    const sent: Vars[] = [];
    server.use(existing(), http.post(COMMAND, async ({ request }) => {
      const v = ((await request.json()) as { variables: Vars }).variables;
      sent.push(v);
      return v.acknowledgedDuplicate ? ok('SHIPPER2') : biz('DUPLICATE_EMAIL', { existingShipperId: 'EXISTING1' });
    }));
    const onRegistered = vi.fn();
    renderApp(<ShipperRegisterForm onRegistered={onRegistered} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    await user.click(await screen.findByRole('button', { name: '登録を続ける' }));
    await waitFor(() => expect(onRegistered).toHaveBeenCalledWith('SHIPPER2'));
    expect(sent).toHaveLength(2);
    expect(sent[1]!.acknowledgedDuplicate).toBe(true);
    // 内容が変わる（承知した）ので、同じキーを使うとサーバーが拒む。新しいキーにする
    expect(sent[1]!.idempotencyKey).not.toBe(sent[0]!.idempotencyKey);
  });

  it('権限が無い（FORBIDDEN）ときは、その旨を示す', async () => {
    server.use(http.post(COMMAND, () => biz('FORBIDDEN')));
    renderApp(<ShipperRegisterForm onRegistered={vi.fn()} onUseExisting={vi.fn()} />);
    const user = userEvent.setup();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '荷主を登録' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('この操作を行う権限がありません');
  });
});
