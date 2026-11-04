import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { ApiError } from './errors';
import { createApi } from './graphql-client';
import type { TypedDocumentNode } from '@graphql-typed-document-node/core';
import { parse } from 'graphql';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const doc = <D, V extends object = Record<string, never>>(source: string) => parse(source) as unknown as TypedDocumentNode<D, V>;

const ShippersQ = doc<{ shippers: { id: string; name: string }[] }>(`query ShippersForClientTest { shippers { id name } }`);
const RegisterM = doc<{ registerShipper: { shipperId: string } }, { idempotencyKey: string }>(`mutation RegisterShipperForClientTest($idempotencyKey: String!) {
  registerShipper(idempotencyKey: $idempotencyKey, name: "a", address: "b", contact: "c", email: "a@b.com") { shipperId } }`);

const api = createApi({ baseUrl: 'http://api.test', headers: () => ({ 'X-Debug-Role': 'Sales', 'X-Debug-User-Id': '01ARZ3NDEKTSV4RRFFQ69G5FAV' }) });

describe('GraphQL クライアント（lib）。Query と Mutation は別のエンドポイント', () => {
  it('Query は /query/graphql へ、認証情報のヘッダーつきで送る', async () => {
    let seen: { role: string | null; user: string | null } | null = null;
    server.use(
      http.post('http://api.test/query/graphql', ({ request }) => {
        seen = { role: request.headers.get('X-Debug-Role'), user: request.headers.get('X-Debug-User-Id') };
        return HttpResponse.json({ data: { shippers: [{ id: '1', name: '山田' }] } });
      }),
    );
    const data = await api.query(ShippersQ);
    expect(data.shippers).toEqual([{ id: '1', name: '山田' }]);
    expect(seen).toEqual({ role: 'Sales', user: '01ARZ3NDEKTSV4RRFFQ69G5FAV' });
  });

  it('Mutation は /command/graphql へ送る', async () => {
    let hit = '';
    server.use(
      http.post('http://api.test/command/graphql', async ({ request }) => {
        hit = ((await request.json()) as { variables: { idempotencyKey: string } }).variables.idempotencyKey;
        return HttpResponse.json({ data: { registerShipper: { shipperId: 'S1' } } });
      }),
    );
    const data = await api.command(RegisterM, { idempotencyKey: 'key-1' });
    expect(data.registerShipper.shipperId).toBe('S1');
    expect(hit).toBe('key-1');
  });

  it('業務エラー（200 で errors）は、コードを持つ ApiError にする', async () => {
    server.use(http.post('http://api.test/query/graphql', () => HttpResponse.json({ data: null, errors: [{ message: 'FORBIDDEN', extensions: { code: 'FORBIDDEN' } }] })));
    await expect(api.query(ShippersQ)).rejects.toMatchObject({ kind: 'business', code: 'FORBIDDEN' });
  });

  it('401 は UNAUTHENTICATED の ApiError（ログインへ誘導できる）', async () => {
    server.use(http.post('http://api.test/query/graphql', () => HttpResponse.json({ errors: [{ message: '認証が必要です', extensions: { code: 'UNAUTHENTICATED' } }] }, { status: 401 })));
    await expect(api.query(ShippersQ)).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
  });

  it('通信の失敗は network の ApiError', async () => {
    server.use(http.post('http://api.test/query/graphql', () => HttpResponse.error()));
    const err = await api.query(ShippersQ).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ kind: 'network' });
  });

  it('基底 URL を省くと、今開いているページと同じ origin に送る（ブラウザでは相対 URL を graphql-request が解釈できない。E2E で判明）', async () => {
    let hit = '';
    const origin = globalThis.location.origin;
    server.use(
      http.post(`${origin}/query/graphql`, () => {
        hit = 'query';
        return HttpResponse.json({ data: { shippers: [] } });
      }),
    );
    const relative = createApi({ headers: () => ({}) });
    await relative.query(ShippersQ);
    expect(hit).toBe('query');
  });
});
