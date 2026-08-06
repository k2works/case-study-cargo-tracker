import { test, expect } from '@playwright/test';

/**
 * ウォーキングスケルトン成立判定（実ブラウザ）。
 * デフォルトシードユーザー（username=ロール小文字 / password=password）でログインし、
 * ナビゲーション遷移とロール別表示制御・htmx 差し替えを検証する。
 */

async function login(page: import('@playwright/test').Page, username: string): Promise<void> {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', 'password');
  await page.click('[data-testid="login-submit"]');
  await expect(page.getByTestId('dashboard-heading')).toBeVisible();
}

test('営業担当者はログインしダッシュボード→荷主登録へ遷移できる', async ({ page }) => {
  await login(page, 'sales');
  await expect(page.getByTestId('nav-/shippers/new')).toBeVisible();
  await page.click('[data-testid="nav-/shippers/new"]');
  await expect(page.getByTestId('shipper-new-heading')).toBeVisible();
});

test('営業担当者の navbar に請求管理・航路管理は表示されない', async ({ page }) => {
  await login(page, 'sales');
  await expect(page.getByTestId('nav-/billing/invoices')).toHaveCount(0);
  await expect(page.getByTestId('nav-/voyages')).toHaveCount(0);
});

test('経理担当者は請求管理に到達でき、見積管理には到達できない(403)', async ({ page }) => {
  await login(page, 'billing');
  await page.goto('/billing/invoices');
  await expect(page.getByTestId('billing-index-heading')).toContainText('請求管理');

  const res = await page.goto('/estimates');
  expect(res?.status()).toBe(403);
});

test('荷主登録フォームで法人を選ぶと契約フィールドが htmx で表示される', async ({ page }) => {
  await login(page, 'sales');
  await page.goto('/shippers/new');
  await expect(page.locator('#contractNumber')).toHaveCount(0);
  await page.selectOption('#shipperType', 'CORPORATE');
  await expect(page.locator('#contractNumber')).toBeVisible();
  await expect(page.locator('#discountRate')).toBeVisible();
});

test('ログアウトするとログイン画面に戻る', async ({ page }) => {
  await login(page, 'sales');
  await page.click('[data-testid="nav-logout"]');
  await expect(page).toHaveURL(/\/login$/);
});
