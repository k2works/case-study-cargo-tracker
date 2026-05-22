import { test, expect } from '@playwright/test';

/**
 * US00 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login でフォームがデフォルト値（admin/password）で表示される
 *   2. ログインボタンを押すとダッシュボードに遷移する
 *   3. ナビゲーションにユーザー名とロールが表示される
 *   4. ログアウトすると /login に戻る
 *   5. 存在しないユーザーでログインするとエラーが表示される
 *
 * 実行前提:
 *   - authms (:8081)、gatewayms (:8080) が起動済み
 *   - admin ユーザー（パスワード: password、ロール: ROLE_ADMIN）が DB に存在
 */

test.describe('US00: 認証', () => {
  test('ログインフォームがデフォルト値付きで表示される', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible();
    await expect(page.locator('#username')).toHaveValue('admin');
    await expect(page.locator('#password')).toHaveValue('password');
    await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible();
  });

  test('admin/password でログインするとダッシュボードに遷移する', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('password');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page).toHaveURL('/', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  });

  test('ログイン後にナビゲーションにユーザー名とロールが表示される', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('password');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page).toHaveURL('/', { timeout: 10_000 });
    await expect(page.getByText('admin')).toBeVisible();
    await expect(page.getByText('ADMIN')).toBeVisible();
  });

  test('ログアウトすると /login に戻る', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('password');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page).toHaveURL('/', { timeout: 10_000 });
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await expect(page).toHaveURL('/login', { timeout: 5_000 });
  });

  test('存在しないユーザーでログインするとエラーが表示される', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('unknown_user_xyz');
    await page.locator('#password').fill('wrongpass');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10_000 });
  });

  test('未認証で / にアクセスすると /login にリダイレクトされる', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/login', { timeout: 5_000 });
  });
});
