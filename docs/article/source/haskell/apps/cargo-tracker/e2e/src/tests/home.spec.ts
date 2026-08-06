import { test, expect } from '@playwright/test';

test.describe('Home', () => {
  test('ホーム画面が表示される', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h1')).toContainText('Cargo Tracker');
    await expect(page.getByRole('link', { name: /ログイン/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /荷主登録/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /貨物予約登録/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /航海登録/ })).toBeVisible();
  });

  test('Health endpoint は UP', async ({ request }) => {
    const res = await request.get('/health');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('UP');
  });
});
