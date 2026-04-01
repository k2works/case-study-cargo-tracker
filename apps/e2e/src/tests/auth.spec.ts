import { test, expect } from '../fixtures';
import { LoginPage } from '../pages/LoginPage';

test.describe('E01: 認証', () => {
  test('正しい認証情報でログインできる', async ({ page, loggedIn }) => {
    // loggedIn フィクスチャがログインを完了しているため、ホームページにいることを確認する
    await expect(page).toHaveURL('/');
    await expect(page.locator('h1')).toContainText('国際貨物輸送管理システム');
  });

  test('ログアウトできる', async ({ page, loggedIn }) => {
    // ナビバーのログアウトボタンをクリックする
    await page.locator('form[action="/logout"] button[type="submit"]').click();

    // ログインページにリダイレクトされ、ログアウト成功メッセージが表示される
    await expect(page).toHaveURL('/login?logout');
    await expect(page.locator('.alert-success')).toContainText('ログアウトしました');
  });

  test('誤った認証情報でエラーが表示される', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('user', 'wrongpassword');

    // エラーURL にリダイレクトされ、エラーメッセージが表示される
    await expect(page).toHaveURL('/login?error');
    await expect(page.locator('.alert-danger')).toContainText(
      'ユーザー名またはパスワードが正しくありません',
    );
  });

  test('未認証でアクセスするとログインページにリダイレクトされる', async ({ page }) => {
    // 認証なしで保護されたページにアクセスする
    await page.goto('/shippers');

    // ログインページにリダイレクトされる
    await expect(page).toHaveURL('/login');
  });
});
