import { test, expect } from '../fixtures';
import { LoginPage } from '../pages/LoginPage';

test.describe('認証', () => {
  test('正しい認証情報でログインしてダッシュボードに遷移する', async ({ page, loggedIn }) => {
    await expect(page).toHaveURL('/dashboard');
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
    await expect(page.getByText('ようこそ、admin さん')).toBeVisible();
  });

  test('誤ったパスワードでエラーメッセージが表示される', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'wrongpassword');
    await expect(loginPage.errorMessage).toBeVisible();
    await expect(page.locator('text=ユーザー名またはパスワードが正しくありません')).toBeVisible();
  });

  test('未認証でダッシュボードにアクセスするとログインページにリダイレクトされる', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('ログアウトするとログイン画面に遷移する', async ({ page, loggedIn }) => {
    await expect(page).toHaveURL('/dashboard');
    await page.locator('button:has-text("ログアウト")').click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('ログアウト後に保護ページにアクセスするとリダイレクトされる', async ({ page, loggedIn }) => {
    await expect(page).toHaveURL('/dashboard');
    await page.locator('button:has-text("ログアウト")').click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });
});
