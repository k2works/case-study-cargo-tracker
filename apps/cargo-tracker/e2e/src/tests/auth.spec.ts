import { test, expect } from '../fixtures';
import { LoginPage } from '../pages/LoginPage';

/** US26: システムにログイン・ログアウトする */
test.describe('US26 認証', () => {
  test('未認証で /` にアクセスすると /login にリダイレクトされる', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });

  test('正しい認証情報でログインしダッシュボードへ遷移できる', async ({ page, loggedIn }) => {
    await expect(page).toHaveURL('/');
    await expect(page.locator('body')).toContainText('CargoTracker');
  });

  test('誤った認証情報で alert-danger が表示される', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'WrongPass!');
    await expect(page.locator('.alert-danger')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('ログアウトでログイン画面へ戻る', async ({ page, loggedIn }) => {
    await page.locator('form[action="/logout"] button[type="submit"]').click();
    await expect(page).toHaveURL(/\/login/);
  });
});
