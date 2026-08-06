import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * ウォーキングスケルトン成立判定: 全ルートの到達性とロール別アクセス制御を検証する。
 * ui_design.md「ロール別画面到達性マトリクス」を正とする。
 */
describe('スケルトン判定: ロール別到達性', () => {
  let ctx: TestApp;

  // path -> 到達可能なロール
  const MATRIX: Record<string, Role[]> = {
    '/': [Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER, Role.TRACKER, Role.HANDLER, Role.BILLING],
    '/estimates': [Role.SALES],
    '/shippers/new': [Role.SALES],
    '/bookings': [Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER],
    '/tracking': [Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER, Role.TRACKER],
    '/handling': [Role.HANDLER, Role.TRACKER],
    '/voyages': [Role.ROUTE_DESIGNER],
    '/billing/invoices': [Role.BILLING],
  };

  const ALL_ROLES = Object.values(Role);

  async function agentFor(role: Role): Promise<TestAgent> {
    const username = `user_${role}`;
    return loginAsTestUser(ctx, { username, roles: [role] });
  }

  beforeEach(async () => {
    ctx = await createTestApp();
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  for (const [path, allowedRoles] of Object.entries(MATRIX)) {
    for (const role of ALL_ROLES) {
      const allowed = allowedRoles.includes(role);
      it(`${role} は ${path} に ${allowed ? '到達できる' : '到達できない(403)'}`, async () => {
        const agent = await agentFor(role);
        const res = await agent.get(path);
        if (allowed) {
          expect(res.status).toBe(200);
        } else {
          expect(res.status).toBe(403);
        }
      });
    }
  }

  it('未認証はすべての業務ルートでログインへ誘導される', async () => {
    for (const path of Object.keys(MATRIX)) {
      const res = await request(ctx.app.getHttpServer()).get(path);
      expect(res.status).toBe(302);
      expect(res.headers.location).toBe('/login');
    }
  });
});
