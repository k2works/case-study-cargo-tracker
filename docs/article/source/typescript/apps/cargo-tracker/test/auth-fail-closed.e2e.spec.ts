import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestApp } from './test-app.js';
import { createTestApp } from './test-app.js';

/**
 * 認証境界の fail-closed 化（ADR-011）の回帰固定。
 * グローバル AuthenticatedGuard により、@Public 未付与ルートは未認証でログインへ誘導され、
 * @Public 付与ルート（ログイン・ヘルス・公開追跡）は未認証で到達できることを担保する。
 */
describe('認証境界 fail-closed (ADR-011)', () => {
  let ctx: TestApp;

  beforeEach(async () => {
    ctx = await createTestApp();
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('公開ルート /login は未認証で 200 を返す', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/login');
    expect(res.status).toBe(200);
  });

  it('公開ルート /health は未認証で 200 を返す', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok' });
  });

  it('公開ルート /public/tracking は未認証で 200 を返す', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/public/tracking');
    expect(res.status).toBe(200);
  });

  it('保護ルート（ダッシュボード /）は未認証でログインへリダイレクトする', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/');
    expect(res.status).toBe(302);
    expect(res.headers.location).toBe('/login');
  });

  it('保護ルート（貨物予約一覧）は未認証でログインへリダイレクトする', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/bookings');
    expect(res.status).toBe(302);
    expect(res.headers.location).toBe('/login');
  });

  it('保護ルート（荷役登録）は未認証でログインへリダイレクトする', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/handling');
    expect(res.status).toBe(302);
    expect(res.headers.location).toBe('/login');
  });

  it('保護ルート（請求書一覧）は未認証でログインへリダイレクトする', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/billing/invoices');
    expect(res.status).toBe(302);
    expect(res.headers.location).toBe('/login');
  });
});
