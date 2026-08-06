import { test, expect } from '../fixtures';
import { EstimateIndexPage, EstimateNewPage, EstimateShowPage } from '../pages/EstimationPage';

test.describe('輸送見積管理', () => {
  test('見積一覧ページが表示される', async ({ page, loggedIn }) => {
    const indexPage = new EstimateIndexPage(page);
    await indexPage.goto();

    await expect(page).toHaveURL('/estimates');
    await expect(indexPage.heading).toHaveText('見積管理');
    await expect(indexPage.newButton).toBeVisible();
    await expect(indexPage.table).toBeVisible();
  });

  test('見積を作成できる', async ({ page, loggedIn }) => {
    const newPage = new EstimateNewPage(page);
    const showPage = new EstimateShowPage(page);

    await newPage.goto();
    await expect(page).toHaveURL('/estimates/new');
    await expect(newPage.heading).toHaveText('見積作成');

    await newPage.fill('JPTYO', 'USLAX', '2027-12-31', 'GENERAL', '1000');
    await newPage.submit();

    // 登録後は詳細ページへリダイレクト
    await expect(page).toHaveURL(/\/estimates\/.+/);
    await expect(showPage.heading).toHaveText('見積詳細');
    await expect(showPage.getDetailValue('出発地')).toHaveText('東京 (JPTYO)');
    await expect(showPage.getDetailValue('目的地')).toHaveText('ロサンゼルス (USLAX)');
    await expect(showPage.getDetailValue('貨物種別')).toHaveText('一般');
  });

  test('見積詳細にルート候補が表示される', async ({ page, loggedIn }) => {
    const newPage = new EstimateNewPage(page);

    await newPage.goto();
    await newPage.fill('JPTYO', 'USNYC', '2027-12-31', 'GENERAL', '500');
    await newPage.submit();

    await expect(page).toHaveURL(/\/estimates\/.+/);

    // ルート候補テーブルが表示される
    const candidatesTable = page.locator('table').nth(0);
    await expect(candidatesTable).toBeVisible();
  });

  test('見積作成フォームからキャンセルして一覧へ戻れる', async ({ page, loggedIn }) => {
    const newPage = new EstimateNewPage(page);

    await newPage.goto();
    await newPage.cancelButton.click();

    await expect(page).toHaveURL('/estimates');
  });
});
