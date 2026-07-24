import { test, expect } from '@playwright/test';

// ウォーキングスケルトン: 全ナビゲーション遷移とロール制御を担保する。
// カレントユーザーは IT1 スタブ（ROLE_SALES）。
test.describe('ウォーキングスケルトン ナビゲーション', () => {
  test('ダッシュボードが表示され navbar が存在する', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('navbar')).toBeVisible();
    await expect(page.getByTestId('page-title')).toHaveText('ダッシュボード');
    await expect(page.getByTestId('current-user')).toHaveText('sales');
  });

  test('ROLE_SALES に許可されたメニューへ遷移できる', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('nav-shippers').click();
    await expect(page).toHaveURL(/\/shippers$/);
    await expect(page.getByTestId('page-title')).toHaveText('荷主一覧');

    await page.goto('/');
    await page.getByTestId('nav-bookings').click();
    await expect(page).toHaveURL(/\/bookings$/);
  });

  test('ROLE_SALES に admin メニューは表示されない（ロール制御）', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('nav-admin')).toHaveCount(0);
    await expect(page.getByTestId('nav-billing')).toHaveCount(0);
  });

  test('全プレースホルダルートへ到達できる', async ({ page }) => {
    for (const path of ['/tracking', '/handling', '/voyages', '/billing/invoices', '/admin/discount-policies']) {
      const res = await page.goto(path);
      expect(res?.status()).toBe(200);
      await expect(page.getByTestId('page-title')).toBeVisible();
    }
  });
});
