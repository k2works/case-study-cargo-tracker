import { test, expect } from '@playwright/test';

/**
 * US24/US25 フロントエンド E2E シナリオ。
 *
 * 実行前提:
 *   - authms (:8081)、routingms (:8083)、gatewayms (:8080) が起動済み
 *   - admin ユーザー（ROLE_ADMIN）が DB に存在
 */

test.describe('US24/US25: 航海スケジュール管理', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('password');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page).toHaveURL('/', { timeout: 10_000 });
  });

  test('ナビゲーションに航海スケジュールメニューが表示される', async ({ page }) => {
    await expect(page.getByRole('link', { name: '航海スケジュール' })).toBeVisible();
  });

  test('航海スケジュールリンクをクリックすると /voyages に遷移する', async ({ page }) => {
    await page.getByRole('link', { name: '航海スケジュール' }).click();
    await expect(page).toHaveURL('/voyages', { timeout: 10_000 });
  });

  test('US24: 新規登録ボタンをクリックすると登録フォームに遷移する', async ({ page }) => {
    await page.goto('/voyages');
    await page.getByRole('button', { name: '新規登録' }).click();
    await expect(page).toHaveURL('/voyages/new', { timeout: 5_000 });
    await expect(page.getByRole('heading', { name: '航海スケジュール新規登録' })).toBeVisible();
  });

  test('US24: 航海スケジュールを新規登録できる', async ({ page }) => {
    const voyageNumber = `V-E2E-${Date.now()}`;
    await page.goto('/voyages/new');

    await page.locator('#voyageNumber').fill(voyageNumber);
    await page.locator('#carrierCode').fill('CARRIER-E2E');
    await page.locator('#carrierName').fill('E2E運送会社');
    await page.locator('#shipName').fill('E2E号');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destUnlocode').fill('USNYC');
    await page.locator('#departureDate').fill('2027-01-01T10:00');
    await page.locator('#arrivalDate').fill('2027-01-15T18:00');
    await page.getByRole('button', { name: '登録' }).click();

    await expect(page).toHaveURL('/voyages', { timeout: 10_000 });
    await expect(page.getByText(voyageNumber)).toBeVisible();
  });

  test('US25: 編集ボタンをクリックすると更新フォームに遷移する', async ({ page }) => {
    const voyageNumber = `V-E2E-EDIT-${Date.now()}`;
    await page.goto('/voyages/new');
    await page.locator('#voyageNumber').fill(voyageNumber);
    await page.locator('#carrierCode').fill('CARRIER-X');
    await page.locator('#carrierName').fill('X運送会社');
    await page.locator('#shipName').fill('X号');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destUnlocode').fill('SGSIN');
    await page.locator('#departureDate').fill('2027-02-01T10:00');
    await page.locator('#arrivalDate').fill('2027-02-10T18:00');
    await page.getByRole('button', { name: '登録' }).click();
    await expect(page).toHaveURL('/voyages', { timeout: 10_000 });

    await page.getByRole('row', { name: new RegExp(voyageNumber) })
      .getByRole('button', { name: '編集' })
      .click();
    await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}/edit`), { timeout: 5_000 });
    await expect(page.getByRole('heading', { name: '航海スケジュール更新' })).toBeVisible();
  });

  test('US25: 航海スケジュールを更新できる', async ({ page }) => {
    const voyageNumber = `V-E2E-UPD-${Date.now()}`;
    await page.goto('/voyages/new');
    await page.locator('#voyageNumber').fill(voyageNumber);
    await page.locator('#carrierCode').fill('CARRIER-Y');
    await page.locator('#carrierName').fill('Y運送会社');
    await page.locator('#shipName').fill('Y号');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destUnlocode').fill('DEHAM');
    await page.locator('#departureDate').fill('2027-03-01T10:00');
    await page.locator('#arrivalDate').fill('2027-03-20T18:00');
    await page.getByRole('button', { name: '登録' }).click();
    await expect(page).toHaveURL('/voyages', { timeout: 10_000 });

    await page.goto(`/voyages/${voyageNumber}/edit`);
    await expect(page.getByRole('heading', { name: '航海スケジュール更新' })).toBeVisible();

    await page.locator('#departureDate').fill('2027-03-05T10:00');
    await page.locator('#arrivalDate').fill('2027-03-25T18:00');
    await page.getByRole('button', { name: '更新' }).click();

    await expect(page).toHaveURL('/voyages', { timeout: 10_000 });
  });
});
