import { test, expect } from '../fixtures';

test.describe('経路設計担当一覧', () => {
  test('経路設計担当一覧ページが表示される', async ({ page, loggedIn }) => {
    await page.goto('/routing/assignments');
    await expect(page.getByRole('heading', { name: '経路設計担当一覧' })).toBeVisible();
  });

  test('ナビゲーションから経路設計担当ページに遷移できる', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('nav').getByRole('link', { name: '経路設計担当' }).click();
    await expect(page).toHaveURL('/routing/assignments');
    await expect(page.getByRole('heading', { name: '経路設計担当一覧' })).toBeVisible();
  });
});
