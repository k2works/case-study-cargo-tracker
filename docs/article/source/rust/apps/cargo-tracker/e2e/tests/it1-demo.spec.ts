import { test, expect } from '@playwright/test';
import { login, logout, bookingStatus, gotoWithRetry, SEED_SHIPPER_ID } from './helpers';

/**
 * IT1 デモ項目（予約基盤）の E2E テスト。ナビゲーション経由で操作する。
 *
 * 1. ログイン/ログアウト・ロール別ナビ出し分け・未認証リダイレクト
 * 2. 個人・法人の荷主登録
 * 3. 一般貨物の予約登録（予約番号発行・状態 仮予約）
 * 4. 危険物貨物の必須検証（申告欠落でエラー）
 */
test.describe.serial('IT1 デモ: 予約基盤', () => {
  test('認証: 未認証はログインへリダイレクトされる', async ({ page }) => {
    await gotoWithRetry(page, '/bookings');
    await page.waitForURL('**/login');
    await expect(page.getByTestId('login-submit')).toBeVisible();
  });

  test('認証: ロール別にナビゲーションが出し分けられログアウトできる', async ({ page }) => {
    // 営業担当者: 貨物予約・見積管理あり、航路管理なし。
    await login(page, 'sales');
    await expect(page.getByTestId('nav-bookings')).toBeVisible();
    await expect(page.getByTestId('nav-estimates')).toBeVisible();
    await expect(page.getByTestId('nav-voyages')).toHaveCount(0);
    await logout(page);

    // 経路設計者: 航路管理あり、見積管理なし。
    await login(page, 'designer');
    await expect(page.getByTestId('nav-voyages')).toBeVisible();
    await expect(page.getByTestId('nav-estimates')).toHaveCount(0);
    await logout(page);
  });

  test('荷主登録: 個人荷主を登録できる', async ({ page }) => {
    await login(page, 'sales');
    await gotoWithRetry(page, '/shippers/new');
    await page.selectOption('select[name="kind"]', 'INDIVIDUAL');
    await page.fill('input[name="name"]', '佐藤 花子');
    await page.fill('input[name="email"]', `sato-${Date.now()}@example.com`);
    await page.getByTestId('shipper-submit').click();
    // 登録成功で予約フォームへ遷移する。
    await page.waitForURL('**/bookings/new');
    await expect(page.getByTestId('shipper-error')).toHaveCount(0);
  });

  test('荷主登録: 法人荷主を割引率付きで登録できる', async ({ page }) => {
    await login(page, 'sales');
    await gotoWithRetry(page, '/shippers/new');
    await page.selectOption('select[name="kind"]', 'CORPORATE');
    await page.fill('input[name="name"]', 'デモ商事株式会社');
    await page.fill('input[name="email"]', `demo-corp-${Date.now()}@example.com`);
    await page.fill('input[name="contract_number"]', 'CTR-2026-9001');
    await page.fill('input[name="discount_rate"]', '0.15');
    await page.getByTestId('shipper-submit').click();
    await page.waitForURL('**/bookings/new');
    await expect(page.getByTestId('shipper-error')).toHaveCount(0);
  });

  test('予約登録: 一般貨物の予約が仮予約状態で登録される', async ({ page }) => {
    await login(page, 'sales');
    await gotoWithRetry(page, '/bookings/new');
    await page.fill('#shipper_id', SEED_SHIPPER_ID);
    await page.fill('#consignee_name', 'LA Trading');
    await page.fill('#consignee_contact', 'consignee@example.com');
    await page.fill('#origin', 'JPOSA');
    await page.fill('#destination', 'USLAX');
    await page.fill('#arrival_deadline', '2026-05-20');
    await page.selectOption('#cargo_type', 'GENERAL');
    await page.fill('#weight', '1200');
    await page.getByTestId('booking-submit').click();

    await page.waitForURL(/\/bookings\/BKG-[^/]+$/);
    expect(await bookingStatus(page)).toBe('PRELIMINARY');
  });

  test('予約登録: 危険物で申告が欠落するとエラーになる', async ({ page }) => {
    await login(page, 'sales');
    await gotoWithRetry(page, '/bookings/new');
    await page.fill('#shipper_id', SEED_SHIPPER_ID);
    await page.fill('#consignee_name', 'LA Trading');
    await page.fill('#consignee_contact', 'consignee@example.com');
    await page.fill('#origin', 'JPOSA');
    await page.fill('#destination', 'USLAX');
    await page.fill('#arrival_deadline', '2026-05-20');
    await page.selectOption('#cargo_type', 'HAZARDOUS');
    await page.fill('#weight', '800');
    // 危険物申告フィールドは未入力のまま送信。
    await page.getByTestId('booking-submit').click();

    await expect(page.getByTestId('booking-error')).toBeVisible();
  });
});
