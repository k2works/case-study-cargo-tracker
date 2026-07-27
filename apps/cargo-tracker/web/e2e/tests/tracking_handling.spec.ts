import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers';

// IT6 デモ: 追跡照会（US18）と荷役作業記録（US15/US16）の受け入れ確認。
// admin は全ロールをバイパスするため、ロール別画面へ到達できる（T1 到達性）。
test.describe('IT6 追跡・荷役', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, USERS.admin);
  });

  test('貨物追跡入力画面に到達でき、追跡番号フォームが表示される（US18）', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('nav-tracking').click();
    await expect(page).toHaveURL(/\/tracking$/);
    await expect(page.getByTestId('page-title')).toHaveText('貨物追跡');
    await expect(page.getByTestId('tracking-id-input')).toBeVisible();
  });

  test('存在しない追跡番号を照会するとエラーメッセージが表示される（US18）', async ({ page }) => {
    await page.goto('/tracking/TRK-20260101-9999');
    await expect(page.getByTestId('flash-error')).toContainText('追跡番号が見つかりません');
  });

  test('不正な形式の追跡番号は照会できない（US18）', async ({ page }) => {
    await page.goto('/tracking?trackingNumber=INVALID');
    await expect(page.getByTestId('flash-error')).toContainText('形式が正しくありません');
  });

  test('荷役作業一覧・登録画面に到達できる（US15）', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('nav-handling').click();
    await expect(page).toHaveURL(/\/handling$/);
    await expect(page.getByTestId('page-title')).toHaveText('荷役作業一覧');

    await page.getByTestId('new-handling').click();
    await expect(page).toHaveURL(/\/handling\/new$/);
    await expect(page.getByTestId('page-title')).toHaveText('荷役作業登録');
  });

  test('引取（CLAIM）選択で荷受人確認フィールドが表示される（US16）', async ({ page }) => {
    await page.goto('/handling/new');
    await expect(page.getByTestId('claim-field')).toBeHidden();
    await page.getByTestId('handling-type').selectOption('CLAIM');
    await expect(page.getByTestId('claim-field')).toBeVisible();
    await expect(page.getByTestId('confirmation-input')).toBeVisible();
  });

  test('公開貨物追跡は未認証でも直接アクセスできる（US18・存在しない番号は 404）', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    const res = await page.goto('/public/tracking/TRK-20260101-9999');
    // 認証リダイレクトされず、公開ページ（404 相当のメッセージ）が返る。
    expect(page.url()).not.toContain('/login');
    expect(res?.status()).toBe(404);
    await ctx.close();
  });
});
