import { test, expect } from '../fixtures';

test.describe('貨物追跡照会', () => {
  test('追跡照会ページが表示される', async ({ page, loggedIn }) => {
    await page.goto('/tracking');
    await expect(page.getByRole('heading', { name: '貨物追跡照会' })).toBeVisible();
    await expect(page.getByPlaceholder(/TRK-/)).toBeVisible();
    await expect(page.getByRole('button', { name: '追跡する' })).toBeVisible();
  });

  test('ナビゲーションから貨物追跡ページに遷移できる', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('nav').getByRole('link', { name: '貨物追跡' }).click();
    await expect(page).toHaveURL('/tracking');
    await expect(page.getByRole('heading', { name: '貨物追跡照会' })).toBeVisible();
  });

  test('存在しない追跡番号を入力するとエラーメッセージが表示される', async ({ page, loggedIn }) => {
    await page.goto('/tracking');
    await page.getByPlaceholder(/TRK-/).fill('TRK-999999');
    await page.getByRole('button', { name: '追跡する' }).click();
    await expect(page.getByText(/追跡番号が見つかりません/)).toBeVisible();
  });
});
