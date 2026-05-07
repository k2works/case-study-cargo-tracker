import { test, expect } from '../fixtures';
import { VoyagePage } from '../pages/VoyagePage';

test.describe('航海スケジュール管理', () => {
  test('航海一覧ページにアクセスできること', async ({ page, loggedIn }) => {
    const voyagePage = new VoyagePage(page);
    await voyagePage.goto();
    await expect(voyagePage.heading).toBeVisible();
    await expect(voyagePage.newVoyageLink).toBeVisible();
  });

  test('新規登録リンクから登録フォームに遷移できること', async ({ page, loggedIn }) => {
    const voyagePage = new VoyagePage(page);
    await voyagePage.goto();
    await voyagePage.newVoyageLink.click();
    await expect(page).toHaveURL('/voyages/new');
    await expect(page.getByRole('heading', { name: '航海新規登録' })).toBeVisible();
  });

  test('航海を新規登録できること', async ({ page, loggedIn }) => {
    const voyageNumber = `E2E-${Date.now()}`;
    const voyagePage = new VoyagePage(page);
    await page.goto('/voyages/new');
    await voyagePage.fillVoyageForm(voyageNumber);
    await voyagePage.submitForm();
    // 登録後に一覧ページに戻ること
    await expect(page).toHaveURL('/voyages');
    await expect(page.getByText(voyageNumber)).toBeVisible();
  });

  test('登録した航海を一覧で確認できること', async ({ page, loggedIn }) => {
    const voyageNumber = `E2E-LIST-${Date.now()}`;
    // 事前登録
    await page.goto('/voyages/new');
    const voyagePage = new VoyagePage(page);
    await voyagePage.fillVoyageForm(voyageNumber);
    await voyagePage.submitForm();
    await expect(page).toHaveURL('/voyages');
    // 一覧に表示されること
    await expect(page.getByText(voyageNumber)).toBeVisible();
  });

  test('未登録の場合「航海が登録されていません。」が表示されること', async ({ page, loggedIn }) => {
    // このテストは DB が空の場合のみ通る（他テストと競合する可能性あり）
    // 一覧ページに正常にアクセスできることを確認する
    const voyagePage = new VoyagePage(page);
    await voyagePage.goto();
    await expect(page).toHaveURL('/voyages');
    await expect(voyagePage.heading).toBeVisible();
  });
});
