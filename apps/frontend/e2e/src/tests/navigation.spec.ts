import { test, expect } from '../fixtures';

test.describe('ナビゲーション', () => {
  test('ダッシュボードリンクをクリックするとダッシュボードに遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/voyages');
    await page.getByRole('link', { name: 'CargoTracker' }).click();
    await expect(page).toHaveURL('/dashboard');
  });

  test('航海スケジュールリンクをクリックすると航海一覧に遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('nav').getByRole('link', { name: '航海スケジュール' }).click();
    await expect(page).toHaveURL('/voyages');
    await expect(page.getByRole('heading', { name: '航海スケジュール管理' })).toBeVisible();
  });

  test('ナビゲーションバーにユーザー名が表示されること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await expect(page.getByText('admin', { exact: true }).first()).toBeVisible();
  });

  test('貨物追跡リンクをクリックすると追跡照会ページに遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('nav').getByRole('link', { name: '貨物追跡' }).click();
    await expect(page).toHaveURL('/tracking');
    await expect(page.getByRole('heading', { name: '貨物追跡照会' })).toBeVisible();
  });

  test('経路設計担当リンクをクリックすると担当一覧に遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.locator('nav').getByRole('link', { name: '経路設計担当' }).click();
    await expect(page).toHaveURL('/routing/assignments');
    await expect(page.getByRole('heading', { name: '経路設計担当一覧' })).toBeVisible();
  });

  test('ログアウトボタンをクリックするとログイン画面に遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await expect(page).toHaveURL('/login');
  });
});
