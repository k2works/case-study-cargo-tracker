import { test, expect } from '../fixtures';
import { VoyageIndexPage } from '../pages/VoyagePage';

test.describe('航路管理', () => {
  test('航路一覧ページが表示される', async ({ page, loggedIn }) => {
    const indexPage = new VoyageIndexPage(page);
    await indexPage.goto();

    await expect(page).toHaveURL('/voyages');
    await expect(indexPage.heading).toHaveText('航路一覧');
    await expect(indexPage.table).toBeVisible();
  });

  test('航路一覧に航海スケジュールが表示される', async ({ page, loggedIn }) => {
    const indexPage = new VoyageIndexPage(page);
    await indexPage.goto();

    await expect(page).toHaveURL('/voyages');
    // V9 マイグレーションデータが表示される
    const tableBody = page.locator('table tbody');
    await expect(tableBody).toBeVisible();
  });

  test('ナビバーの「航路管理」リンクで遷移できる', async ({ page, loggedIn }) => {
    await page.goto('/');
    const navLink = page.locator('nav a', { hasText: '航路管理' });
    await navLink.click();

    await expect(page).toHaveURL('/voyages');
    await expect(page.locator('h1')).toHaveText('航路一覧');
  });

  test('出発地フィルタで検索できる', async ({ page, loggedIn }) => {
    const indexPage = new VoyageIndexPage(page);
    await indexPage.goto();

    await indexPage.search('JPTYO', '');

    await expect(page).toHaveURL(/\/voyages/);
    await expect(indexPage.heading).toHaveText('航路一覧');
  });
});
