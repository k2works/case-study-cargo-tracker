import { test, expect } from '@playwright/test';

function randomShipperId(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let s = 'SHP-';
  for (let i = 0; i < 6; i++) s += chars[Math.floor(Math.random() * chars.length)];
  return s;
}

test.describe('US02 個人荷主登録', () => {
  test('個人荷主を登録できる', async ({ page }) => {
    await page.goto('/shippers/new');
    await expect(page.locator('h1')).toContainText('荷主登録');

    const shipperId = randomShipperId();
    await page.locator('#shipperId').fill(shipperId);
    await page.locator('#email').fill(`taro.${Date.now()}@example.com`);
    await page.locator('#address').fill('4-2-8 Shibakoen, Minato-ku, Tokyo');
    await page.locator('#kind-individual').check();

    await page.locator('button[type="submit"]').click();

    await expect(page.locator('h1')).toContainText('登録結果');
    await expect(page.locator('.alert-success')).toBeVisible();
  });
});

test.describe('US03 法人荷主登録', () => {
  test('法人荷主を登録できる', async ({ page }) => {
    await page.goto('/shippers/new');

    const shipperId = randomShipperId();
    await page.locator('#shipperId').fill(shipperId);
    await page.locator('#email').fill(`corp.${Date.now()}@example.com`);
    await page.locator('#address').fill('1-1-1 Marunouchi, Chiyoda-ku, Tokyo');
    await page.locator('#kind-corporate').check();
    await page.locator('#corporateNumber').fill('1234567890123');
    await page.locator('#contractRank').selectOption('Gold');

    await page.locator('button[type="submit"]').click();

    await expect(page.locator('h1')).toContainText('登録結果');
    await expect(page.locator('.alert-success')).toBeVisible();
  });
});
