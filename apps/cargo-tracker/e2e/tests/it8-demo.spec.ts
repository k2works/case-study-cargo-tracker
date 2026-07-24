import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * IT8 デモ項目の E2E テスト（精算処理・US23）。
 *
 * シードデータ（seed バイナリ）が用意する状態:
 * - BKG-0011 引取済・個人荷主（US23 精算デモ用）
 *
 * 共有 DB を変更するため直列実行する（playwright.config: workers=1）。
 * 経理担当者が 料金算出→確定→精算書発行→入金確認→精算完了 まで通す。
 */
test.describe.serial('IT8 デモ: 精算処理', () => {
  test('US23: 確定料金から精算書を発行し入金確認で精算完了する', async ({ page }) => {
    await login(page, 'billing');

    // 料金算出（引取済 BKG-0011）→ 確定。
    await page.getByTestId('nav-charges').click();
    await page.waitForURL('**/charges');
    await page.getByTestId('charge-new-link').click();
    await page.fill('[data-testid="charge-booking-id"]', 'BKG-0011');
    await page.getByTestId('charge-submit').click();
    await page.waitForURL('**/charges/FRC-*');
    page.on('dialog', (d) => d.accept());
    await page.getByTestId('charge-confirm').click();
    await page.waitForURL('**/charges/FRC-*');
    await expect(page.getByTestId('charge-status')).toContainText('確定');

    // 確定料金から精算書を発行 → 請求金額（料金＋消費税10%）。
    await page.getByTestId('invoice-issue').click();
    await page.waitForURL('**/billing/invoices/INV-*');
    // 基本料金 200,000 → 消費税 20,000 → 請求 220,000。
    await expect(page.getByTestId('invoice-charge-total')).toContainText('200000');
    await expect(page.getByTestId('invoice-tax')).toContainText('20000');
    await expect(page.getByTestId('invoice-total-amount')).toContainText('220000');
    await expect(page.getByTestId('invoice-payment-status')).toContainText('未払い');

    // 入金確認 → 精算完了（入金確認済）。
    await page.getByTestId('invoice-confirm-payment').click();
    await page.waitForURL('**/billing/invoices/INV-*');
    await expect(page.getByTestId('invoice-payment-status')).toContainText('入金確認済');
  });

  test('US23: 精算書一覧から発行済み精算書を確認できる', async ({ page }) => {
    await login(page, 'billing');
    await page.getByTestId('nav-billing').click();
    await page.waitForURL('**/billing/invoices');
    await expect(page.getByTestId('invoice-list-title')).toBeVisible();
    await expect(page.getByTestId('invoice-table')).toContainText('BKG-0011');
  });
});
