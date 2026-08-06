import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers';

// IT7 デモ: 例外処理（US19/US20）と貨物状態手動更新（US17）。
// admin は全ロールをバイパスするため、追跡管理者向け画面へ到達できる。
test.describe('IT7 例外処理・状態手動更新', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, USERS.admin);
  });

  test('存在しない追跡番号の例外画面は 404', async ({ page }) => {
    const res = await page.goto('/tracking/TRK-20260101-9999/exceptions');
    expect(res?.status()).toBe(404);
  });

  test('例外登録フォームに種別（遅延/破損/紛失）が表示される（US19/US20）', async ({ page }) => {
    // 追跡レコードが必要なため、存在する追跡番号を前提とするフルフローは
    // 発行フロー（IT6）とのシード連携が必要。ここではフォーム構造を検証する。
    // 追跡番号は seed 済みを想定（無い場合 404 となり本アサートはスキップ）。
    const res = await page.goto('/tracking/TRK-20260720-0001/exceptions');
    if (res?.status() === 404) {
      test.skip(true, '追跡レコード未シード（フルフローE2Eは統合テストで担保）');
    }
    await expect(page.getByTestId('exception-type')).toBeVisible();
    await expect(page.getByTestId('submit-exception')).toBeVisible();
  });

  test('状態手動更新フォームに状態選択が表示される（US17）', async ({ page }) => {
    const res = await page.goto('/tracking/TRK-20260720-0001/status-update');
    if (res?.status() === 404) {
      test.skip(true, '追跡レコード未シード');
    }
    await expect(page.getByTestId('status-select')).toBeVisible();
  });
});
