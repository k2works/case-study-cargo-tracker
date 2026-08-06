import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestApp, TestAgent } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * 精算経路（ROLE_BILLING）の認可負テスト（tester#1/#2）。
 * 経理担当者以外のロールでは請求書一覧・料金算出・入金確認いずれも 403 で拒否されることを固定する。
 * 認可は @Roles(Role.BILLING) + RolesGuard による fail-closed を担保する。
 */
describe('精算経路の認可 (ROLE_BILLING)', () => {
  let ctx: TestApp;
  let sales: TestAgent;
  let handler: TestAgent;

  beforeEach(async () => {
    ctx = await createTestApp();
    sales = await loginAsTestUser(ctx, { username: 'sales1', roles: [Role.SALES] });
    handler = await loginAsTestUser(ctx, { username: 'handler1', roles: [Role.HANDLER] });
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('非 BILLING ロールは請求書一覧 GET /billing/invoices を 403 で拒否される', async () => {
    expect((await sales.get('/billing/invoices')).status).toBe(403);
    expect((await handler.get('/billing/invoices')).status).toBe(403);
  });

  it('非 BILLING ロールは請求書発行 POST /billing/invoices を 403 で拒否される', async () => {
    const res = await sales.post('/billing/invoices').type('form').send({ bookingId: 'x' });
    expect(res.status).toBe(403);
  });

  it('非 BILLING ロールは入金確認 POST /billing/invoices/:n/confirm を 403 で拒否される', async () => {
    const res = await sales.post('/billing/invoices/INV-0001/confirm').type('form').send({});
    expect(res.status).toBe(403);
  });
});
