import { test, expect } from '@playwright/test';

// US25 (IT2): 既存航海スケジュールを更新する。
// 画面遷移: 航海詳細 → 更新する → 更新フォーム (/voyages/:vn/edit)
//   → POST /voyages/:vn/update → PRG → /voyages/:vn?flash=updated

test.describe('US25 航海スケジュール更新 (IT2 PRG)', () => {
  test('航海を登録 → 詳細から更新 → 区間を差し替えて確定 → flash 表示', async ({ page }) => {
    const voyageNumber = `V${Date.now().toString().slice(-7)}`;

    // 1. 航海登録 (1 区間)
    await page.goto('/voyages/new');
    await page.locator('#voyageNumber').fill(voyageNumber);
    await page.locator('select[name="movement1Departure"]').selectOption('JPTYO');
    await page.locator('select[name="movement1Arrival"]').selectOption('USNYC');
    await page.locator('input[name="movement1DepartureTime"]').fill('2026-07-01T09:00');
    await page.locator('input[name="movement1ArrivalTime"]').fill('2026-07-15T18:00');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}$`));

    // 2. 詳細画面の「更新する」リンクを経由
    const editLink = page.locator(`a[href$="/voyages/${voyageNumber}/edit"]`);
    await expect(editLink).toBeVisible();
    await editLink.click();
    await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}/edit$`));
    await expect(page.locator('h1')).toContainText('航海');

    // 3. 区間 1+2 (経由地追加) で全置換
    await page.locator('select[name="movement1Departure"]').selectOption('JPTYO');
    await page.locator('select[name="movement1Arrival"]').selectOption('USLAX');
    await page.locator('input[name="movement1DepartureTime"]').fill('2026-07-02T09:00');
    await page.locator('input[name="movement1ArrivalTime"]').fill('2026-07-12T18:00');
    await page.locator('select[name="movement2Departure"]').selectOption('USLAX');
    await page.locator('select[name="movement2Arrival"]').selectOption('USNYC');
    await page.locator('input[name="movement2DepartureTime"]').fill('2026-07-13T09:00');
    await page.locator('input[name="movement2ArrivalTime"]').fill('2026-07-20T18:00');
    await page.locator('button[type="submit"]').click();

    // 4. PRG: /voyages/:vn?flash=updated に遷移
    await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}\\?flash=updated`));
    // 詳細画面の区間表示が 2 件 (経由地 USLAX を含む) に更新されている
    await expect(page.locator('body')).toContainText('USLAX');
  });

  test('区間連続性違反は /voyages/:vn/edit?error=leg-continuity に PRG リダイレクト', async ({ page }) => {
    const voyageNumber = `V${Date.now().toString().slice(-7)}`;

    // 既存航海作成
    await page.goto('/voyages/new');
    await page.locator('#voyageNumber').fill(voyageNumber);
    await page.locator('select[name="movement1Departure"]').selectOption('JPTYO');
    await page.locator('select[name="movement1Arrival"]').selectOption('USNYC');
    await page.locator('input[name="movement1DepartureTime"]').fill('2026-07-01T09:00');
    await page.locator('input[name="movement1ArrivalTime"]').fill('2026-07-15T18:00');
    await page.locator('button[type="submit"]').click();

    // 不連続な 2 区間を投入
    await page.goto(`/voyages/${voyageNumber}/edit`);
    await page.locator('select[name="movement1Departure"]').selectOption('JPTYO');
    await page.locator('select[name="movement1Arrival"]').selectOption('USLAX');
    await page.locator('input[name="movement1DepartureTime"]').fill('2026-07-02T09:00');
    await page.locator('input[name="movement1ArrivalTime"]').fill('2026-07-12T18:00');
    await page.locator('select[name="movement2Departure"]').selectOption('CNSHA'); // USLAX とつながらない
    await page.locator('select[name="movement2Arrival"]').selectOption('USNYC');
    await page.locator('input[name="movement2DepartureTime"]').fill('2026-07-13T09:00');
    await page.locator('input[name="movement2ArrivalTime"]').fill('2026-07-20T18:00');
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}/edit\\?error=leg-continuity`));
    await expect(page.locator('.alert-danger')).toBeVisible();
    await expect(page.locator('.alert-danger')).toContainText('連続性');
  });
});
