import { test, expect } from '@playwright/test';

function randomId(prefix: string): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let s = `${prefix}-`;
  for (let i = 0; i < 6; i++) s += chars[Math.floor(Math.random() * chars.length)];
  return s;
}

test.describe('US04 貨物予約登録', () => {
  test('荷主登録 → 貨物予約 を通せる (E2E デモシナリオ)', async ({ page }) => {
    const shipperId = randomId('SHP');

    await page.goto('/shippers/new');
    await page.locator('#shipperId').fill(shipperId);
    await page.locator('#email').fill(`booking.${Date.now()}@example.com`);
    await page.locator('#address').fill('4-2-8 Shibakoen, Minato-ku, Tokyo');
    await page.locator('#kind-individual').check();
    await page.locator('button[type="submit"]').click();
    await expect(page.locator('.alert-success')).toBeVisible();

    await page.goto('/bookings/new');
    await expect(page.locator('h1')).toContainText('貨物予約');

    const bookingId = randomId('BK');
    await page.locator('#bookingId').fill(bookingId);
    await page.locator('#shipperId').fill(shipperId);
    await page.locator('#origin').selectOption('JPTYO');
    await page.locator('#destination').selectOption('USNYC');
    await page.locator('#deadline').fill('2026-08-31T18:00');

    await page.locator('button[type="submit"]').click();

    await expect(page.locator('h1')).toContainText('予約結果');
    await expect(page.locator('.alert-success')).toBeVisible();
  });
});
