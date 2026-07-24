import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * IT7 デモ項目の E2E テスト（破損・紛失例外 / 輸送料金算出 / 法人割引）。
 *
 * シードデータ（seed バイナリ）が用意する状態:
 * - TRK-DEMO-DMG 積込済（BKG-0010・US20 破損/紛失例外デモ用）
 * - BKG-0009 引取済・法人荷主（山田物流・割引率 10%・US21/US22 料金算出デモ用）
 *
 * 共有 DB を変更するため直列実行する（playwright.config: workers=1）。
 */
test.describe.serial('IT7 デモ: 破損紛失例外・料金算出・法人割引', () => {
  test('US20: 紛失例外を選ぶと緊急エスカレーションの注意が表示される（可視性）', async ({
    page,
  }) => {
    await login(page, 'tracker');
    await page.goto('/tracking/TRK-DEMO-DMG/exceptions/new');

    // 初期（遅延）は escalation 注意が非表示。
    await expect(page.getByTestId('escalation-notice')).toBeHidden();
    // 破損を選んでも非表示。
    await page.selectOption('[data-testid="exception-type-select"]', 'DAMAGE');
    await expect(page.getByTestId('escalation-notice')).toBeHidden();
    // 紛失を選ぶと escalation 注意が表示される。
    await page.selectOption('[data-testid="exception-type-select"]', 'LOST');
    await expect(page.getByTestId('escalation-notice')).toBeVisible();
  });

  test('US20: 追跡管理者が破損例外を登録すると貨物状態が例外発生になる', async ({ page }) => {
    await login(page, 'tracker');
    await page.goto('/tracking/TRK-DEMO-DMG/exceptions/new');
    await page.selectOption('[data-testid="exception-type-select"]', 'DAMAGE');
    await page.fill('#un_locode', 'SGSIN');
    await page.fill('#occurred_at', '2026-05-06T09:00');
    await page.fill('#description', '荷崩れによる破損');
    page.on('dialog', (d) => d.accept());
    await page.getByTestId('exception-submit').click();

    await page.waitForURL('**/tracking/TRK-DEMO-DMG');
    await expect(page.getByTestId('transport-status')).toContainText('例外');
  });

  test('US21/US22: 経理担当者が法人予約の料金を算出すると割引が適用され確定できる', async ({
    page,
  }) => {
    await login(page, 'billing');
    await page.getByTestId('nav-charges').click();
    await page.waitForURL('**/charges');
    await page.getByTestId('charge-new-link').click();
    await page.waitForURL('**/charges/new');

    // 引取済・法人予約 BKG-0009 の料金を算出。
    await page.fill('[data-testid="charge-booking-id"]', 'BKG-0009');
    await page.getByTestId('charge-submit').click();

    // 料金詳細へリダイレクト。基本料金 200,000・法人割引 10%（20,000）・合計 180,000。
    await page.waitForURL('**/charges/FRC-*');
    await expect(page.getByTestId('charge-base-amount')).toContainText('200000');
    // 法人割引が表示される（US22・法人時のみ・可視性）。
    await expect(page.getByTestId('charge-discount')).toBeVisible();
    await expect(page.getByTestId('charge-total-amount')).toContainText('180000');

    // 確定 → 状態が確定に。
    page.on('dialog', (d) => d.accept());
    await page.getByTestId('charge-confirm').click();
    await page.waitForURL('**/charges/FRC-*');
    await expect(page.getByTestId('charge-status')).toContainText('確定');
  });
});
