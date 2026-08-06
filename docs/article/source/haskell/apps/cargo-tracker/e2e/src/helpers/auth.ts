import { Page, expect } from '@playwright/test';

/**
 * E2E ログイン共通ヘルパ (IT5 追加)
 *
 * IT3 U-07 (ロール別認可) で `/shippers`, `/bookings`, `/voyages` 等の
 * 一覧画面ナビは認証ユーザーにのみ表示されるため、各テストで事前ログインが必要。
 *
 * seed 済ユーザー (共通パスワード: "password"):
 *   admin / sales / router / tracker / handler / accountant / shipper / consignee
 *
 * 使い方:
 *   import { login } from '../helpers/auth';
 *   test('...', async ({ page }) => {
 *     await login(page, 'admin@example.com');
 *     // 認証後のテスト
 *   });
 *
 * 全ロール用ショートカット:
 *   await loginAsAdmin(page);
 *   await loginAsSales(page);
 *   etc.
 */

export const SEED_PASSWORD = 'password';

/**
 * 任意の seed ユーザーでログインする。
 * 成功すると `/` (ダッシュボード) にリダイレクトされる。
 */
export async function login(
  page: Page,
  email: string,
  password: string = SEED_PASSWORD,
): Promise<void> {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.locator('button[type="submit"]').click();
  // ログイン成功で / または /home にリダイレクトされる
  await expect(page).toHaveURL(/\/(home)?(\?.*)?$/, { timeout: 5000 });
}

export const loginAsAdmin = (page: Page) => login(page, 'admin@example.com');
export const loginAsSales = (page: Page) => login(page, 'sales@example.com');
export const loginAsRouter = (page: Page) => login(page, 'router@example.com');
export const loginAsTracker = (page: Page) => login(page, 'tracker@example.com');
export const loginAsHandler = (page: Page) => login(page, 'handler@example.com');
export const loginAsAccountant = (page: Page) => login(page, 'accountant@example.com');
export const loginAsShipper = (page: Page) => login(page, 'shipper@example.com');
