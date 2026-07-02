import { test, expect } from '@playwright/test';

// IT6 US21: 料金算出画面 (/pricing/calculate)
// pricing_rule / currency_rate はマイグレーションで JPY / USD シード投入済み。

test.describe('料金算出 (US21, IT6)', () => {
  test('ホームから遷移でき、フォームが表示される', async ({ page }) => {
    await page.goto('/');
    const card = page.getByRole('link', { name: /送料計算/ });
    await expect(card).toBeVisible();
    await card.click();
    await expect(page).toHaveURL(/\/pricing\/calculate$/);
    await expect(page.locator('#cargoCategory')).toBeVisible();
    await expect(page.locator('#distanceKm')).toBeVisible();
    await expect(page.locator('#weightKg')).toBeVisible();
  });

  test('General / JPY で料金が算出される', async ({ page }) => {
    await page.goto('/pricing/calculate');
    await page.locator('#cargoCategory').selectOption('General');
    await page.locator('#distanceKm').fill('1000');
    await page.locator('#weightKg').fill('500');
    await page.locator('#baseCurrency').fill('JPY');
    await page.locator('#targetCurrency').fill('JPY');
    await page.locator('#discountRate').fill('0');
    await page.locator('button[type="submit"]').click();

    await expect(page.getByTestId('calc-result-success')).toBeVisible();
    await expect(page.getByTestId('calc-result-success')).toContainText('JPY');
  });

  test('割引率 10% を指定すると結果カードに反映される', async ({ page }) => {
    await page.goto('/pricing/calculate');
    await page.locator('#cargoCategory').selectOption('General');
    await page.locator('#distanceKm').fill('100');
    await page.locator('#weightKg').fill('100');
    await page.locator('#discountRate').fill('10');
    await page.locator('button[type="submit"]').click();

    await expect(page.getByTestId('calc-result-success')).toBeVisible();
  });
});
