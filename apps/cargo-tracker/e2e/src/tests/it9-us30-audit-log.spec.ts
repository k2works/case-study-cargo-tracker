import { test, expect } from '../fixtures';
import { AuditLogPage } from '../pages/AuditLogPage';

/** IT9 US30 E2E: 監査ログ閲覧 (MasterAdmin 限定) + マスタ管理ナビ動線。 */

test.describe('IT9 US30: 監査ログ閲覧', () => {
  test('ナビバー「マスタ管理」dropdown → 監査ログリンク経由で /admin/audit-logs に遷移できる (MasterAdmin)', async ({ page, loggedIn }) => {
    // admin ユーザーは MasterAdmin ロールを持つ前提
    await page.goto('/');
    const audit = new AuditLogPage(page);
    await audit.openFromNavDropdown();
    await expect(page).toHaveURL(/\/admin\/audit-logs/);
    await expect(page.locator('h1:has-text("監査ログ")')).toBeVisible();
  });

  test('監査ログ一覧画面で検索フォーム 5 フィールド + テーブルが表示される', async ({ page, loggedIn }) => {
    const audit = new AuditLogPage(page);
    await audit.gotoList();
    await expect(page.locator('input[name="from"]')).toBeVisible();
    await expect(page.locator('input[name="to"]')).toBeVisible();
    await expect(page.locator('input[name="operator"]')).toBeVisible();
    await expect(page.locator('select[name="action"]')).toBeVisible();
    await expect(page.locator('input[name="limit"]')).toBeVisible();
    await expect(page.locator('button:has-text("検索")')).toBeVisible();
    // 操作種別 dropdown に 6 種 (AuditAction enum) + すべて = 7 オプション
    const options = await page.locator('select[name="action"] option').count();
    expect(options).toBe(7);
  });

  test('action フィルタで絞り込み検索が動作する', async ({ page, loggedIn }) => {
    const audit = new AuditLogPage(page);
    await audit.gotoList();
    await audit.filterByAction('IssuePayment');
    // URL に action パラメータが含まれる
    await expect(page).toHaveURL(/action=IssuePayment/);
  });
});
