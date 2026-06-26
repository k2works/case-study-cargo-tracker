import { test, expect } from '@playwright/test';

test.describe('US24 航海登録', () => {
  test('多区間 (JPTYO → HKHKG → USNYC) の航海を登録できる', async ({ page }) => {
    await page.goto('/voyages/new');
    await expect(page.locator('h1')).toContainText('航海');

    const voyageNumber = `V${Date.now().toString().slice(-7)}`;
    await page.locator('#voyageNumber').fill(voyageNumber);

    await page.locator('select[name="movement1Departure"]').selectOption('JPTYO');
    await page.locator('select[name="movement1Arrival"]').selectOption('HKHKG');
    await page.locator('input[name="movement1DepartureTime"]').fill('2026-07-01T09:00');
    await page.locator('input[name="movement1ArrivalTime"]').fill('2026-07-05T18:00');

    await page.locator('select[name="movement2Departure"]').selectOption('HKHKG');
    await page.locator('select[name="movement2Arrival"]').selectOption('USNYC');
    await page.locator('input[name="movement2DepartureTime"]').fill('2026-07-06T10:00');
    await page.locator('input[name="movement2ArrivalTime"]').fill('2026-07-20T15:00');

    await page.locator('button[type="submit"]').click();

    await expect(page.locator('h1')).toContainText('航海登録結果');
    await expect(page.locator('.alert-success')).toBeVisible();
  });
});
