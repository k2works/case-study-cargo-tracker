import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers';

// US26: システムにログインする
test.describe('US26: 認証・認可', () => {
  test('未認証で保護ページにアクセスするとログイン画面へリダイレクトされる', async ({ page }) => {
    await page.goto('/shippers');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByTestId('page-title')).toHaveText('ログイン');
  });

  test('誤った認証情報ではエラーが表示される', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('username').fill('admin');
    await page.getByTestId('password').fill('wrong');
    await page.getByTestId('submit').click();
    await expect(page.getByTestId('flash-error')).toContainText('正しくありません');
  });

  test('正しい認証でログインしダッシュボードへ遷移する', async ({ page }) => {
    await login(page, USERS.sales);
    await expect(page.getByTestId('current-user')).toHaveText('sales');
    await expect(page.getByTestId('logout')).toBeVisible();
  });

  test('ロール不足のルートは 403 になる（sales が航路管理へ）', async ({ page }) => {
    await login(page, USERS.sales);
    const res = await page.goto('/voyages');
    expect(res?.status()).toBe(403);
  });

  test('管理者は全ルートにアクセスできる', async ({ page }) => {
    await login(page, USERS.admin);
    for (const path of ['/shippers', '/bookings', '/tracking', '/handling', '/voyages', '/billing/invoices', '/admin/discount-policies']) {
      const res = await page.goto(path);
      expect(res?.status(), path).toBe(200);
    }
  });

  test('ログアウトするとセッションが破棄されログイン画面へ戻る', async ({ page }) => {
    await login(page, USERS.sales);
    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/\/login$/);
    // 破棄後は保護ページに入れない
    await page.goto('/shippers');
    await expect(page).toHaveURL(/\/login$/);
  });
});
