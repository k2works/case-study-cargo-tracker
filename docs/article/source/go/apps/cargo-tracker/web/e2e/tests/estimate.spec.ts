import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers';

// IT3 デモ項目の受け入れ E2E（US01 輸送見積作成）。見積管理は ROLE_SALES。

function daysFromNow(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

test.beforeEach(async ({ page }) => {
  await login(page, USERS.sales);
});

test.describe('US01: 輸送見積作成', () => {
  test('ナビから見積管理へ到達し見積を作成できる', async ({ page }) => {
    // ナビ導線
    await page.getByTestId('nav-estimates').click();
    await expect(page).toHaveURL(/\/estimates$/);
    await expect(page.getByTestId('page-title')).toHaveText('見積一覧');

    await page.getByTestId('new-estimate').click();
    await expect(page).toHaveURL(/\/estimates\/new$/);
    await page.getByTestId('origin').fill('JPTYO');
    await page.getByTestId('destination').fill('USLAX');
    // 十分先の期限（候補が間に合う）
    await page.getByTestId('arrival-deadline').fill(daysFromNow(120));
    await page.getByTestId('cargo-type').selectOption('GENERAL');
    await page.getByTestId('weight').fill('1200.5');
    await page.getByTestId('submit').click();

    // PRG で見積詳細へ、ルート候補が表示される
    await expect(page).toHaveURL(/\/estimates\/[0-9a-f-]+$/);
    await expect(page.getByTestId('page-title')).toHaveText('見積詳細');
    await expect(page.getByTestId('candidates')).toBeVisible();
    await expect(page.getByTestId('candidate-row').first()).toBeVisible();
  });
});
