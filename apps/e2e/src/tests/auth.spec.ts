import { test, expect } from '../fixtures';
import { LoginPage } from '../pages/LoginPage';

test.describe('E01: 認証', () => {
  test('正しい認証情報でログインできる', async ({ page, loggedIn }) => {
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  });

  test('ログアウトできる', async ({ page, loggedIn }) => {
    await page.locator('form[action="/logout"] button[type="submit"]').click();
    await expect(page).toHaveURL('/login?logout');
    await expect(page.locator('.alert-success')).toContainText('You have been logged out');
  });

  test('誤った認証情報でエラーが表示される', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('user', 'wrongpassword');
    await expect(page).toHaveURL('/login?error');
    await expect(page.locator('.alert-danger')).toContainText('Invalid username or password');
  });

  test('未認証でアクセスするとログインページにリダイレクトされる', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });
});
