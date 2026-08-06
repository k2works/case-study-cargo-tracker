import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';
import { NAV_ITEMS } from '../src/views/layout/nav-items.js';

/**
 * ナビ定義（nav-items）とコントローラの @Roles ガードの実整合を自動検証する（IT5 Try T2）。
 * nav-items をデータソースとし、
 * - ナビに表示されるロールは該当 href に GET 200
 * - 表示されないロールは 403
 * を全組み合わせで確認する。ナビ定義とガードがずれると必ず失敗する。
 */
describe('ナビ×コントローラのロール整合（Try T2）', () => {
  let ctx: TestApp;

  const ALL_ROLES = Object.values(Role);

  /**
   * ナビ表示の可否とアクセス可否が一致しない既知の例外を href 単位で allowlist 化する。
   * 例: 公開ページや、ナビ外ロールにも閲覧を許すエンドポイントはここに明示する。
   * 現時点では例外なし（ナビ定義とガードは完全一致が期待値）。
   */
  const ACCESS_EXCEPTIONS: Record<string, readonly Role[]> = {};

  async function agentFor(role: Role): Promise<TestAgent> {
    return loginAsTestUser(ctx, { username: `nav_${role}`, roles: [role] });
  }

  beforeEach(async () => {
    ctx = await createTestApp();
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  for (const item of NAV_ITEMS) {
    // roles 空配列は全ロール可のため、非表示ロールの 403 検証対象は存在しない。
    const shownRoles = item.roles.length === 0 ? ALL_ROLES : item.roles;
    const extraAllowed = ACCESS_EXCEPTIONS[item.href] ?? [];

    for (const role of ALL_ROLES) {
      const navShows = shownRoles.includes(role);
      const shouldReach = navShows || extraAllowed.includes(role);
      it(`${role} は ${item.href}（${item.label}）に ${shouldReach ? '到達できる(200)' : '到達できない(403)'}`, async () => {
        const agent = await agentFor(role);
        const res = await agent.get(item.href);
        if (shouldReach) {
          expect(res.status).toBe(200);
        } else {
          expect(res.status).toBe(403);
        }
      });
    }
  }
});
