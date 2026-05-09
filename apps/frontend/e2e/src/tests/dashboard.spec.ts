import { test, expect } from '../fixtures';

test.describe('ダッシュボード', () => {
  test('ダッシュボードにメニューカードが表示される', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
    // カード領域のリンクを確認（ナビバーと重複するため main 内で絞る）
    const main = page.locator('main')
    await expect(main.getByRole('link', { name: '貨物追跡' })).toBeVisible();
    await expect(main.getByRole('link', { name: '荷役記録' })).toBeVisible();
  });

  test('ダッシュボードの貨物予約カードをクリックすると予約一覧に遷移する', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('main').getByRole('link', { name: '貨物予約' }).click();
    await expect(page).toHaveURL('/bookings');
    await expect(page.getByRole('heading', { name: '貨物予約管理' })).toBeVisible();
  });

  test('ダッシュボードの貨物追跡カードをクリックすると追跡照会ページに遷移する', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('main').getByRole('link', { name: '貨物追跡' }).click();
    await expect(page).toHaveURL('/tracking');
    await expect(page.getByRole('heading', { name: '貨物追跡照会' })).toBeVisible();
  });

  test('ダッシュボードの荷役記録カードをクリックすると荷役記録ページに遷移する', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('main').getByRole('link', { name: '荷役記録' }).click();
    await expect(page).toHaveURL('/handling/activities');
    await expect(page.getByRole('heading', { name: '荷役作業記録' })).toBeVisible();
  });

  test('ダッシュボードの経路設計担当カードをクリックすると担当一覧に遷移する', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('main').getByRole('link', { name: '経路設計担当' }).click();
    await expect(page).toHaveURL('/routing/assignments');
    await expect(page.getByRole('heading', { name: '経路設計担当一覧' })).toBeVisible();
  });
});
