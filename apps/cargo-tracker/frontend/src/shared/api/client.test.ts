import { describe, expect, it, vi, afterEach } from 'vitest';
import { ApiError, commandClient, queryClient } from './client';

function mockFetch(status: number, body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      new Response(body === null ? '' : JSON.stringify(body), { status }),
    ),
  );
}

afterEach(() => vi.unstubAllGlobals());

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
