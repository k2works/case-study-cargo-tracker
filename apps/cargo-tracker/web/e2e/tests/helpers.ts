import { Page, expect } from '@playwright/test';

// シードユーザー（パスワードはすべて 'password'）
export const USERS = {
  admin: { username: 'admin', password: 'password' }, // ROLE_ADMIN（全機能）
  sales: { username: 'sales', password: 'password' }, // ROLE_SALES, ROLE_SHIPPER
};

// login はログイン画面から認証し、ダッシュボードへ遷移する。
export async function login(page: Page, user: { username: string; password: string }): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('username').fill(user.username);
  await page.getByTestId('password').fill(user.password);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/$/);
}
