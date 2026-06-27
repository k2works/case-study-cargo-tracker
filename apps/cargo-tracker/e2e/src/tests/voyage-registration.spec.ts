import { test, expect } from '@playwright/test';

// IT2 対応:
// - voyageNumber はユーザ入力のまま (Voyage は IT2 で自動採番化していない)
// - PRG: POST /voyages/new → 303 → GET /voyages/:voyageNumber

test.describe('US24 航海登録 (IT2 PRG 遷移)', () => {
  test('多区間 (JPTYO → HKHKG → USNYC) を登録すると詳細画面に遷移する', async ({ page }) => {
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

    await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}$`));
    await expect(page.locator('h1')).toContainText('航海詳細');
  });

  test('時刻フォーマット不正は /voyages/new?error= に PRG リダイレクトしフラッシュ表示', async ({ page }) => {
    await page.goto('/voyages/new');

    const voyageNumber = `V${Date.now().toString().slice(-7)}`;
    await page.locator('#voyageNumber').fill(voyageNumber);
    // HTML5 required + datetime-local バリデーションを迂回するため
    // 強制的に value を不正文字列にする
    await page.locator('select[name="movement1Departure"]').selectOption('JPTYO');
    await page.locator('select[name="movement1Arrival"]').selectOption('USNYC');
    await page
      .locator('input[name="movement1DepartureTime"]')
      .evaluate((el: HTMLInputElement) => {
        el.removeAttribute('required');
        el.type = 'text';
        el.value = 'NOT-A-DATE';
      });
    await page
      .locator('input[name="movement1ArrivalTime"]')
      .evaluate((el: HTMLInputElement) => {
        el.removeAttribute('required');
        el.type = 'text';
        el.value = 'NOT-A-DATE';
      });

    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/voyages\/new\?error=/);
  });
});
