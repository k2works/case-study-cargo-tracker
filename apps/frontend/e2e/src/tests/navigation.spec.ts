import { test, expect } from '../fixtures';

test.describe('ナビゲーション', () => {
  test('ダッシュボードリンクをクリックするとダッシュボードに遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/voyages');
    await page.getByRole('link', { name: 'CargoTracker' }).click();
    await expect(page).toHaveURL('/dashboard');
  });

  test('航海スケジュールリンクをクリックすると航海一覧に遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.getByRole('link', { name: '航海スケジュール' }).click();
    await expect(page).toHaveURL('/voyages');
    await expect(page.getByRole('heading', { name: '航海スケジュール管理' })).toBeVisible();
  });

  test('ナビゲーションバーにユーザー名が表示されること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await expect(page.getByText('admin', { exact: true }).first()).toBeVisible();
  });

  test('ログアウトボタンをクリックするとログイン画面に遷移すること', async ({ page, loggedIn }) => {
    await page.goto('/dashboard');
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await expect(page).toHaveURL('/login');
  });
});
