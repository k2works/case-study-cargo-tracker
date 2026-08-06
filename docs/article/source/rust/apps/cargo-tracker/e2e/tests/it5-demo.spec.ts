import { test, expect } from '@playwright/test';
import { login, navigateToBookingDetail, bookingStatus } from './helpers';

/**
 * IT5 デモ項目の E2E テスト（追跡・荷役）。ナビゲーション経由で操作する。
 *
 * シードデータ（seed バイナリ）が用意する状態:
 * - BKG-0006 予約確定（US14 追跡番号発行）
 * - BKG-0007 追跡番号発行済（TRK-DEMO-0007・受領待ち。US15/US16 荷役・US17 手動更新）
 *
 * 共有 DB を変更するため直列実行する（playwright.config: workers=1）。
 */
test.describe.serial('IT5 デモ: 追跡・荷役（ナビゲーション経由）', () => {
  test('US14: 確定予約の詳細から追跡番号を発行すると追跡番号発行済になる', async ({ page }) => {
    await login(page, 'designer');
    await navigateToBookingDetail(page, 'BKG-0006');
    expect(await bookingStatus(page)).toBe('CONFIRMED');

    page.on('dialog', (d) => d.accept());
    await page.getByTestId('issue-tracking-btn').click();
    await page.waitForURL('**/bookings/BKG-0006');
    expect(await bookingStatus(page)).toBe('TRACKING_ISSUED');
  });

  test('US15: 荷役管理から受領を記録すると追跡状態が受領済になる', async ({ page }) => {
    await login(page, 'handler');
    await page.getByTestId('nav-handling').click();
    await page.waitForURL('**/handling');
    await page.getByTestId('handling-new-link').click();
    await page.waitForURL('**/handling/new');

    await page.fill('#tracking_number', 'TRK-DEMO-0007');
    await page.selectOption('#handling_type', 'RECEIVE');
    await page.fill('#un_locode', 'JPOSA');
    await page.fill('#completion_time', '2026-05-01T10:00');
    page.on('dialog', (d) => d.accept());
    await page.getByTestId('handling-submit').click();

    // 記録成立（一覧へリダイレクト、またはルート相違警告の 200）。
    await page.waitForLoadState('networkidle');

    // 追跡詳細で受領済を確認（追跡管理者で照会）。
    await login(page, 'tracker');
    await page.goto('/tracking/TRK-DEMO-0007');
    await expect(page.getByTestId('transport-status')).toContainText('受領済');
  });

  test('US16: 引取選択時に荷受人確認フィールドが表示される', async ({ page }) => {
    await login(page, 'handler');
    await page.goto('/handling/new');
    // 初期は非表示。
    await expect(page.getByTestId('receipt-field')).toBeHidden();
    await page.selectOption('#handling_type', 'CLAIM');
    await expect(page.getByTestId('receipt-field')).toBeVisible();
  });

  test('US17: 追跡管理者が手動更新すると搭載中になる', async ({ page }) => {
    await login(page, 'tracker');
    await page.getByTestId('nav-tracking').click();
    await page.waitForURL('**/tracking');
    await page.fill('#tracking_number', 'TRK-DEMO-0007');
    await page.getByTestId('tracking-search-btn').click();
    await page.waitForURL('**/tracking/TRK-DEMO-0007');

    await page.selectOption('#status', 'ONBOARD_CARRIER');
    await page.fill('#un_locode', 'JPOSA');
    await page.fill('#event_time', '2026-05-02T09:00');
    page.on('dialog', (d) => d.accept());
    await page.getByTestId('tracking-update-submit').click();
    await page.waitForURL('**/tracking/TRK-DEMO-0007');

    await expect(page.getByTestId('transport-status')).toContainText('搭載中');
  });
});
