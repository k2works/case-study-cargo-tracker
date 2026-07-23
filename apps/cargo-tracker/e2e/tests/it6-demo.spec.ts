import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * IT6 デモ項目の E2E テスト（見積・公開照会・遅延例外）。ナビゲーション経由で操作する。
 *
 * シードデータ（seed バイナリ）が用意する状態:
 * - 航海 V0001 直行（JPOSA→USLAX・GENERAL・到着 2026-05-14）
 * - TRK-DEMO-EXC 積込済（BKG-0008・US18 公開照会 / US19 遅延例外デモ用）
 *
 * 共有 DB を変更するため直列実行する（playwright.config: workers=1）。
 */
test.describe.serial('IT6 デモ: 見積・公開照会・遅延例外', () => {
  test('US01: 営業担当者が輸送要件を入力するとルート候補が表示され見積番号が発行される', async ({
    page,
  }) => {
    await login(page, 'sales');
    await page.getByTestId('nav-estimates').click();
    await page.waitForURL('**/estimates');
    await page.getByTestId('estimate-new-link').click();
    await page.waitForURL('**/estimates/new');

    await page.fill('#origin', 'JPOSA');
    await page.fill('#destination', 'USLAX');
    await page.fill('#arrival_deadline', '2026-05-20');
    await page.selectOption('#cargo_type', 'GENERAL');
    await page.fill('#weight', '1200');
    await page.getByTestId('estimate-submit').click();

    // 見積詳細へリダイレクトされ、見積番号（EST-）とルート候補が表示される。
    await page.waitForURL('**/estimates/EST-*');
    await expect(page.getByTestId('estimate-id')).toContainText('EST-');
    await expect(page.getByTestId('estimate-candidates')).toContainText('V0001');
  });

  test('US01: 期限内ルートが無い要件では候補無しが通知される', async ({ page }) => {
    await login(page, 'sales');
    await page.goto('/estimates/new');

    // 到着 2026-05-14 より前の期限 → 候補 0。
    await page.fill('#origin', 'JPOSA');
    await page.fill('#destination', 'USLAX');
    await page.fill('#arrival_deadline', '2026-05-10');
    await page.selectOption('#cargo_type', 'GENERAL');
    await page.fill('#weight', '800');
    await page.getByTestId('estimate-submit').click();

    await page.waitForURL('**/estimates/EST-*');
    await expect(page.getByTestId('estimate-no-route')).toBeVisible();
  });

  test('US01: 危険物を選ぶと危険物クラス入力フィールドが表示される', async ({ page }) => {
    await login(page, 'sales');
    await page.goto('/estimates/new');
    // 初期（GENERAL）は非表示。
    await expect(page.getByTestId('hazardous-field')).toBeHidden();
    await page.selectOption('#cargo_type', 'HAZARDOUS');
    await expect(page.getByTestId('hazardous-field')).toBeVisible();
  });

  test('US18: 荷主が未認証で公開ページから追跡状態を照会できる', async ({ page }) => {
    // ログインせずに公開ページを照会する。
    await page.goto('/public/tracking/TRK-DEMO-EXC');
    await expect(page.getByTestId('public-transport-status')).toContainText('積込済');
    await expect(page.getByTestId('public-tracking-timeline')).toBeVisible();
    // 共有 URL 導線が表示される（未認証で共有できる旨）。
    await expect(page.getByTestId('public-tracking-share-url')).toContainText(
      '/public/tracking/TRK-DEMO-EXC',
    );
  });

  test('US19: 追跡管理者が遅延例外を登録し対応報告すると状態と履歴が更新される', async ({
    page,
  }) => {
    await login(page, 'tracker');
    await page.goto('/tracking/TRK-DEMO-EXC');

    // 遅延例外を登録 → 貨物状態が例外発生に。
    await page.getByTestId('exception-new-link').click();
    await page.waitForURL('**/tracking/TRK-DEMO-EXC/exceptions/new');
    await page.selectOption('#exception_type', 'DELAY');
    await page.fill('#un_locode', 'SGSIN');
    await page.fill('#occurred_at', '2026-05-05T12:00');
    await page.fill('#description', '台風による遅延');
    page.on('dialog', (d) => d.accept());
    await page.getByTestId('exception-submit').click();

    await page.waitForURL('**/tracking/TRK-DEMO-EXC');
    await expect(page.getByTestId('transport-status')).toContainText('例外');

    // 対応報告 → 例外解決・履歴記録。
    await page.goto('/tracking/TRK-DEMO-EXC/exceptions/0/resolve');
    await page.fill('#new_arrival', '2026-06-25');
    await page.fill('#resolution_notes', '代替便を手配・新到着予定 6/25');
    await page.getByTestId('exception-resolve-submit').click();

    await page.waitForURL('**/tracking/TRK-DEMO-EXC');
    // 解決後は例外表示ではなくなり、末尾イベント（積込済）に復帰する。
    await expect(page.getByTestId('transport-status')).toContainText('積込済');
  });
});
