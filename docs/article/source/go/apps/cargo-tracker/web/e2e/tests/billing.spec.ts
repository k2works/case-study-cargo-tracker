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

  // US21→US22→US23 フルフロー: CLAIMED の法人予約（seed BKG-BILL0001・割引10%）で
  // 料金算出→法人割引→精算書発行→入金確認→CONFIRMED までを画面から通す。
  test('精算フルフロー: 発行→法人割引→入金確認（US21/US22/US23）', async ({ page }) => {
    await page.goto('/billing/invoices');
    await page.getByTestId('booking-input').fill('BKG-BILL0001');
    await page.getByTestId('generate-invoice').click();

    // PRG で請求書詳細へ遷移し、金額内訳（基本料金・法人割引・合計）が表示される。
    await expect(page).toHaveURL(/\/billing\/invoices\/INV-\d{8}-\d{4}$/);
    await expect(page.getByTestId('amount-breakdown')).toBeVisible();
    await expect(page.getByTestId('discount-amount')).toBeVisible();
    await expect(page.getByTestId('payment-status')).toHaveText('未払い');

    // 入金確認 → CONFIRMED（精算済み）。
    await page.getByTestId('confirm-payment').click();
    await expect(page.getByTestId('payment-status')).toHaveText('精算済み');
    await expect(page.getByTestId('paid-at')).toBeVisible();
  });
});
