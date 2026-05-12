import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';

test.describe('荷主管理', () => {
  test('荷主一覧ページにアクセスできること', async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await shipperPage.goto();
    await expect(shipperPage.heading).toBeVisible();
    await expect(shipperPage.newShipperLink).toBeVisible();
  });

  test('新規登録リンクから登録フォームに遷移できること', async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await shipperPage.goto();
    await shipperPage.newShipperLink.click();
    await expect(page).toHaveURL('/shippers/new');
    await expect(page.getByRole('heading', { name: '荷主の登録' })).toBeVisible();
  });

  test('個人荷主を登録できること', async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await page.goto('/shippers/new');
    const email = `test.individual.${Date.now()}@example.com`;
    await shipperPage.fillIndividualForm('テスト 太郎', email, '03-1234-5678');
    await shipperPage.submitForm();
    await expect(page).toHaveURL('/shippers', { timeout: 10000 });
  });

  test('法人荷主を登録できること', async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await page.goto('/shippers/new');
    const email = `test.corporate.${Date.now()}@example.com`;
    await shipperPage.fillCorporateForm('株式会社テスト', email, '03-9876-5432', 'CONTRACT-001', '10');
    await shipperPage.submitForm();
    await expect(page).toHaveURL('/shippers', { timeout: 10000 });
  });

  test('法人種別選択で契約番号・割引率フィールドが表示されること', async ({ page, loggedIn }) => {
    await page.goto('/shippers/new');
    // 初期状態では法人フィールドが非表示
    await expect(page.getByLabel('契約番号')).not.toBeVisible();
    // 法人を選択
    await page.getByLabel('法人').check();
    // 法人フィールドが表示される
    await expect(page.getByLabel('契約番号')).toBeVisible();
    await expect(page.getByLabel('割引率（0〜30%）')).toBeVisible();
  });
});
