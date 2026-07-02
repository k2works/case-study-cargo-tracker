import { test, expect } from '@playwright/test';

// IT6 US26: 通知一覧画面 (/notifications)
// bookingId 未指定時は案内メッセージ、指定時は該当通知一覧を表示。

test.describe('通知一覧 (US26, IT6)', () => {
  test('ホームから遷移できる', async ({ page }) => {
    await page.goto('/');
    const card = page.getByRole('link', { name: /通知一覧/ });
    await expect(card).toBeVisible();
    await card.click();
    await expect(page).toHaveURL(/\/notifications$/);
  });

  test('bookingId 未指定時は案内メッセージが表示される', async ({ page }) => {
    await page.goto('/notifications');
    await expect(page.locator('body')).toContainText('bookingId');
  });

  test('存在しない bookingId 指定時は 0 件でもエラーにならない', async ({ page }) => {
    await page.goto('/notifications?bookingId=BKG-NOTEXIST');
    await expect(page).toHaveURL(/\/notifications\?bookingId=BKG-NOTEXIST$/);
    // ページ本体はレンダリングされる
    await expect(page.locator('h1, h2')).toBeVisible();
  });
});
