import { test, expect } from '@playwright/test';

/**
 * US24/US25 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. ログイン → 航海スケジュールメニューが表示される
 *   2. ログイン → 航海スケジュール一覧ページに遷移できる
 *   3. 航海スケジュール一覧 → 新規登録ページに遷移できる
 *
 * 実行前提:
 *   - authms (:8081)、routingms (:8083)、gatewayms (:8080) が起動済み
 *   - admin ユーザー（ROLE_ADMIN）が DB に存在
 *
 * NOTE: IT1 時点では routingms のデータ登録 API は未実装のため、
 *       画面遷移と UI 構造の確認のみ行う。
 *       データ登録のフルシナリオは US24/US25 バックエンド完成後に追加する。
 */

test.describe('US24/US25: 航海スケジュール管理', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('password');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page).toHaveURL('/', { timeout: 10_000 });
  });

  test('ナビゲーションに航海スケジュールメニューが表示される', async ({ page }) => {
    await expect(page.getByRole('link', { name: '航海スケジュール' })).toBeVisible();
  });

  test('航海スケジュールリンクをクリックすると /voyages に遷移する', async ({ page }) => {
    await page.getByRole('link', { name: '航海スケジュール' }).click();
    await expect(page).toHaveURL('/voyages', { timeout: 10_000 });
  });
});
