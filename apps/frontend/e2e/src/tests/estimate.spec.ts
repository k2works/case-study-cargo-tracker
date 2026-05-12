import { test, expect } from '../fixtures';
import { EstimatePage } from '../pages/EstimatePage';

test.describe('輸送見積', () => {
  test('輸送見積ページにアクセスできること', async ({ page, loggedIn }) => {
    const estimatePage = new EstimatePage(page);
    await estimatePage.goto();
    await expect(estimatePage.heading).toBeVisible();
    await expect(estimatePage.submitButton).toBeVisible();
  });

  test('見積フォームを送信してルート候補が表示されること', async ({ page, loggedIn }) => {
    const estimatePage = new EstimatePage(page);
    await estimatePage.goto();
    await estimatePage.fillEstimateForm('JPTYO', 'USNYC', '2026-09-30', '500');
    await estimatePage.submitForm();
    // ルート候補テーブルが表示されること
    await expect(page.getByText('ルート候補')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('V001')).toBeVisible();
    await expect(page.getByText('V002')).toBeVisible();
  });

  test('見積番号が発行されること', async ({ page, loggedIn }) => {
    const estimatePage = new EstimatePage(page);
    await estimatePage.goto();
    await estimatePage.fillEstimateForm('JPTYO', 'CNSHA', '2026-09-30', '100');
    await estimatePage.submitForm();
    // 見積番号（UUID形式）が表示されること
    await expect(page.getByText(/見積番号:/)).toBeVisible({ timeout: 10000 });
  });
});
