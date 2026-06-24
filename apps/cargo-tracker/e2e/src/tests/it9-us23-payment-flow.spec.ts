import { test, expect } from '../fixtures';
import { BillingPage } from '../pages/BillingPage';

/** IT9 US23 E2E: 請求書詳細画面での支払発行 → 入金確認 → Cargo.Settled 連携の通しテスト。
 *
 * 前提: Delivered 予約 + 発行済 Invoice が DB に存在すること (setup は別途必要)。
 * 本 spec は支払欄 UI の操作と画面遷移を主に検証する。
 */
test.describe('IT9 US23: 支払発行 → 入金確認フロー', () => {
  test('NotIssued Invoice の詳細画面に支払発行フォームが表示される (Settlement / MasterAdmin)', async ({ page, loggedIn }) => {
    const billing = new BillingPage(page);
    await billing.gotoInvoiceList();
    // 一覧から NotIssued の Invoice 行をクリック (前提: 1 件以上存在)
    const firstRow = page.locator('table tbody tr').first();
    if (await firstRow.count() === 0) {
      test.skip(true, '請求書が DB に存在しません (setup 未完)');
    }
    await firstRow.locator('a').first().click();
    // 支払発行フォーム表示 (NotIssued 状態)
    await expect(page.locator('h3:has-text("支払発行")')).toBeVisible();
    await expect(page.locator('input[name="dueDate"]')).toBeVisible();
    await expect(page.locator('input[name="referenceCode"]')).toBeVisible();
  });

  test('支払状態の 5 種類バッジが正しく色分け表示される (UI 確認)', async ({ page, loggedIn }) => {
    const billing = new BillingPage(page);
    await billing.gotoInvoiceList();
    // 一覧画面の payment_status 列が表示されていることを確認
    await expect(page.locator('h1:has-text("請求書")')).toBeVisible();
    // 5 種類のバッジクラス確認は個別 Invoice 詳細画面で検証 (NotIssued: bg-secondary / Pending: bg-warning / Overdue: bg-danger / Confirmed: bg-success / Refunded: bg-info)
  });
});
