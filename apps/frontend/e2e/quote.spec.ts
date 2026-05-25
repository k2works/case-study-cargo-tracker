import { test, expect, Page } from '@playwright/test';

/**
 * US07/US01: 航海スケジュール検索から輸送見積作成までのフロントエンド E2E シナリオ。
 *
 * 実行前提:
 *   - authms (:8081)、bookingms (:8082)、routingms (:8083)、gatewayms (:8080) が起動済み
 *   - admin ユーザー（ROLE_ADMIN）が DB に存在
 *   - 各サービスは local-h2 等で各テストごとにクリーン状態が望ましい
 *
 * Read Model（Projection）は Axon @EventHandler が非同期更新するため、
 * 航海登録直後の検索・見積作成直後の一覧表示には待機を設ける。
 */

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('password');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

async function registerVoyage(page: Page, voyageNumber: string) {
  await page.goto('/voyages/new');
  await page.locator('#voyageNumber').fill(voyageNumber);
  await page.locator('#carrierCode').fill('MOL-E2E');
  await page.locator('#carrierName').fill('E2E海運');
  await page.locator('#shipName').fill('E2E丸');
  await page.locator('#originUnlocode').fill('JPTYO');
  await page.locator('#destUnlocode').fill('USNYC');
  await page.locator('#departureDate').fill('2027-06-01T10:00');
  await page.locator('#arrivalDate').fill('2027-06-15T18:00');
  await page.getByRole('button', { name: '登録' }).click();
  await expect(page).toHaveURL('/voyages', { timeout: 10_000 });
}

test.describe('US07/US01: 見積作成フロー', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('ナビゲーションに見積管理メニューが表示される', async ({ page }) => {
    await expect(page.getByRole('link', { name: '見積管理' })).toBeVisible();
  });

  test('見積管理リンクをクリックすると /quotes に遷移する', async ({ page }) => {
    await page.getByRole('link', { name: '見積管理' }).click();
    await expect(page).toHaveURL('/quotes', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: '見積一覧' })).toBeVisible();
  });

  test('US07/US01: 航海検索 → 候補選択 → 見積作成 → 一覧・詳細を確認できる', async ({ page }) => {
    const voyageNumber = `V-Q-${Date.now()}`;

    // 前提: 検索対象の航海スケジュールを登録（US24）
    await registerVoyage(page, voyageNumber);

    // 見積作成フォームを開く
    await page.goto('/quotes/new');
    await expect(page.getByRole('heading', { name: '新規見積作成' })).toBeVisible();

    // 輸送要件を入力
    await page.locator('#shipperId').fill('S-E2E-001');
    await page.locator('#origin').fill('JPTYO');
    await page.locator('#destination').fill('USNYC');
    await page.locator('#weightKg').fill('1500');
    await page.locator('#productName').fill('電子部品');
    await page.locator('#arrivalDeadline').fill('2027-09-30');
    await page.locator('#validUntil').fill('2027-08-31');

    // US07: 航海検索（Read Model 反映後、登録航海が候補に表示される）
    await page.getByRole('button', { name: '航海を検索' }).click();
    await expect(page.getByText(voyageNumber)).toBeVisible({ timeout: 15_000 });

    // 候補を選択して見積を作成
    await page.getByLabel(`候補 ${voyageNumber}`).check();
    await page.getByRole('button', { name: '見積を作成' }).click();

    // US01: 見積一覧へ遷移
    await expect(page).toHaveURL('/quotes', { timeout: 10_000 });

    // 一覧の見積番号リンクから詳細を開く（Read Model 反映待ち）
    const firstQuoteLink = page.locator('table tbody tr td button').first();
    await expect(firstQuoteLink).toBeVisible({ timeout: 15_000 });
    await firstQuoteLink.click();

    // 詳細ページに基本情報とルート候補が表示される
    await expect(page.getByRole('heading', { name: /^見積 / })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('JPTYO')).toBeVisible();
    await expect(page.getByText('USNYC')).toBeVisible();
  });
});
