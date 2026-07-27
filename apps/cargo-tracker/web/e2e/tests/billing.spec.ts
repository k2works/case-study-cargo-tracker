import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers';

// IT8 デモ: 精算（US21 料金算出・US22 法人割引・US23 精算）。
// admin は全ロールをバイパスするため、経理担当者向け請求管理へ到達できる。
test.describe('IT8 精算・請求管理', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, USERS.admin);
  });

  test('請求書一覧に到達でき、精算書発行フォームが表示される（US21）', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('nav-billing').click();
    await expect(page).toHaveURL(/\/billing\/invoices$/);
    await expect(page.getByTestId('page-title')).toHaveText('請求書一覧');
    await expect(page.getByTestId('generate-invoice')).toBeVisible();
  });

  test('引取済みでない予約の精算はエラーになる（US21）', async ({ page }) => {
    await page.goto('/billing/invoices');
    await page.getByTestId('booking-input').fill('CARGO-NOT-CLAIMED');
    await page.getByTestId('generate-invoice').click();
    // 引取済みでない or 存在しない予約はエラーメッセージ。
    await expect(page.getByTestId('flash-error')).toBeVisible();
  });

  test('存在しない請求書詳細は 404', async ({ page }) => {
    const res = await page.goto('/billing/invoices/INV-20260101-9999');
    expect(res?.status()).toBe(404);
  });
});
