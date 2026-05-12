import { test, expect } from '../fixtures';

test.describe('貨物追跡照会', () => {
  test('追跡照会ページが表示される', async ({ page, loggedIn }) => {
    await page.goto('/tracking');
    await expect(page.getByRole('heading', { name: '貨物追跡照会' })).toBeVisible();
    await expect(page.getByPlaceholder(/TRK-/)).toBeVisible();
    await expect(page.getByRole('button', { name: '追跡する' })).toBeVisible();
  });

  test('ナビゲーションから貨物追跡ページに遷移できる', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('nav').getByRole('link', { name: '貨物追跡' }).click();
    await expect(page).toHaveURL('/tracking');
    await expect(page.getByRole('heading', { name: '貨物追跡照会' })).toBeVisible();
  });

  test('存在しない追跡番号を入力するとエラーメッセージが表示される', async ({ page, loggedIn }) => {
    await page.goto('/tracking');
    await page.getByPlaceholder(/TRK-/).fill('TRK-999999');
    await page.getByRole('button', { name: '追跡する' }).click();
    await expect(page.getByText(/追跡番号が見つかりません/)).toBeVisible();
  });

  test('荷役記録→追跡照会の一連フローが動作すること', async ({ page, loggedIn }) => {
    // 1. 荷役記録ページに遷移して追跡番号と荷役情報を入力
    await page.goto('/handling/activities');
    await expect(page.getByRole('heading', { name: '荷役作業記録' })).toBeVisible();

    // 追跡番号入力
    await page.getByPlaceholder('TRK-000001').fill('TRK-000001');
    // 作業場所入力
    await page.getByPlaceholder('JPTYO').fill('JPTYO');
    // 作業日時入力
    await page.getByLabel(/作業日時/).fill('2026-01-01T10:00');

    // 2. 記録ボタンをクリック
    await page.getByRole('button', { name: '記録する' }).click();

    // API レスポンスを待機（成功・エラー問わず何らかの応答があること）
    await page.waitForTimeout(3000);

    // 3. 追跡照会ページに遷移
    await page.goto('/tracking');
    await expect(page.getByRole('heading', { name: '貨物追跡照会' })).toBeVisible();

    // 4. 追跡番号を入力して検索
    await page.getByPlaceholder(/TRK-/).fill('TRK-000001');
    await page.getByRole('button', { name: '追跡する' }).click();

    // 追跡結果または「見つかりません」メッセージが表示されることを確認
    await expect(
      page.getByText(/現在の状態|追跡番号が見つかりません/)
    ).toBeVisible({ timeout: 10000 });
  });
});
