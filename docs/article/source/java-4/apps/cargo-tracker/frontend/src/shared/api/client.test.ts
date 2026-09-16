import { describe, expect, it, vi, afterEach, beforeEach } from 'vitest';
import { ApiError, commandClient, queryClient } from './client';
import { useAuthStore } from '../auth/authStore';

function mockFetch(status: number, body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      new Response(body === null ? '' : JSON.stringify(body), { status }),
    ),
  );
}

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ user: null });
});
afterEach(() => vi.unstubAllGlobals());

function sentHeaders(): Record<string, string> {
  const call = (globalThis.fetch as unknown as { mock: { calls: unknown[][] } }).mock.calls[0];
  return ((call?.[1] as RequestInit).headers ?? {}) as Record<string, string>;
}

describe('資格情報の送出', () => {
  // ログインできるのに業務画面がすべて 401 になる、という壊れ方を防ぐ。
  // 単体テストは fetch を差し替えるので、付け忘れはここでしか捕まらない。
  it('ログインしていれば Authorization を送る（問い合わせ）', async () => {
    useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 'jwt-1' } });
    mockFetch(200, { items: [] });

    await queryClient('/booking/shippers');

    expect(sentHeaders().Authorization).toBe('Bearer jwt-1');
  });

  it('ログインしていれば Authorization を送る（操作）', async () => {
    useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 'jwt-2' } });
    mockFetch(201, { shipperId: 'x' });

    await commandClient('/booking/shippers', {});

    expect(sentHeaders().Authorization).toBe('Bearer jwt-2');
  });

  it('ログインしていなければ Authorization を送らない', async () => {
    mockFetch(200, { items: [] });

    await queryClient('/booking/shippers');

    expect(sentHeaders().Authorization).toBeUndefined();
  });

  it('401 を受けたら認証を捨てる（再ログインへ導く）', async () => {
    useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 'jwt-3' } });
    mockFetch(401, { code: 'UNAUTHENTICATED' });

    await expect(queryClient('/booking/shippers')).rejects.toBeInstanceOf(ApiError);
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('403 では認証を捨てない（権限が無いだけで、ログインは有効）', async () => {
    useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 'jwt-4' } });
    mockFetch(403, { code: 'FORBIDDEN' });

    await expect(queryClient('/booking/shippers')).rejects.toBeInstanceOf(ApiError);
    expect(useAuthStore.getState().user).not.toBeNull();
  });
});

describe('queryClient', () => {
  it('200 は値として返す', async () => {
    mockFetch(200, { shipperId: 'SHP-1' });

    const result = await queryClient<{ shipperId: string }>('/booking/shippers/SHP-1');

    expect(result).toEqual({ state: 'ready', value: { shipperId: 'SHP-1' } });
  });

  it('202 は失敗にせず「反映中」として返す', async () => {
    mockFetch(202, { message: '登録を受け付けました。反映までしばらくお待ちください' });

    const result = await queryClient('/booking/shippers/unknown');

    expect(result.state).toBe('pending');
    expect(result.state === 'pending' && result.message).toContain('反映');
  });

  it('404 は失敗として投げる（202 と区別する）', async () => {
    mockFetch(404, { code: 'NOT_FOUND', message: '見つかりません' });

    await expect(queryClient('/booking/shippers/none')).rejects.toBeInstanceOf(ApiError);
  });
});

describe('commandClient', () => {
  it('409 は理由つきで投げる', async () => {
    mockFetch(409, {
      code: 'SHIPPER_EMAIL_DUPLICATE',
      message: 'このメールアドレスは既に登録されています',
    });

    await expect(commandClient('/booking/shippers', {})).rejects.toMatchObject({
      status: 409,
      body: { code: 'SHIPPER_EMAIL_DUPLICATE' },
    });
  });

  it('422 は理由つきで投げる', async () => {
    mockFetch(422, { code: 'BUSINESS_RULE_VIOLATION', message: '割引率は 0.0000〜0.3000 の範囲です' });

    await expect(commandClient('/booking/shippers', {})).rejects.toMatchObject({ status: 422 });
  });

  it('201 は本文を返す', async () => {
    mockFetch(201, { shipperId: 'SHP-9' });

    await expect(commandClient('/booking/shippers', {})).resolves.toEqual({ shipperId: 'SHP-9' });
  });
});
