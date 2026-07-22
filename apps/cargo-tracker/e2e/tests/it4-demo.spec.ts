import { test, expect } from '@playwright/test';
import {
  login,
  navigateToBookingDetail,
  navigateToRouteDesign,
  bookingStatus,
} from './helpers';

/**
 * IT4 デモ項目の E2E テスト（予約状態機械）。ナビゲーション経由で操作する。
 *
 * ナビゲーション経路:
 *   ログイン → ダッシュボード → navbar「貨物予約」→ 予約一覧 → 詳細 →（経路設計者は）経路設計
 *
 * シードデータ（seed バイナリ）が用意する状態:
 * - BKG-0001 仮受付（US06 経路設計依頼）
 * - BKG-0004 経路設計中・期限内直行便あり（US11 確定紐付け・US13 差し戻し）
 * - BKG-0005 経路設計中・期限超過のみ（US10 条件調整・US13 キャンセル）
 * - BKG-0002 経路提案中＋確定経路（US12 荷主通知・US13 予約確定）
 *
 * 状態機械を伴い共有 DB を変更するため直列実行する（playwright.config: workers=1）。
 */
test.describe.serial('IT4 デモ: 予約状態機械（ナビゲーション経由）', () => {
  test('US06: 予約一覧から詳細へ辿り経路設計依頼で経路設計中になる', async ({ page }) => {
    await login(page, 'sales');
    await navigateToBookingDetail(page, 'BKG-0001');
    expect(await bookingStatus(page)).toBe('PRELIMINARY');

    await page.getByTestId('assign-routing-btn').click();
    await page.waitForURL('**/bookings/BKG-0001');
    expect(await bookingStatus(page)).toBe('ROUTE_DESIGNING');
  });

  test('US10: 経路設計画面へ辿り期限超過の候補を条件調整で期限内に再算出する', async ({ page }) => {
    await login(page, 'designer');
    await navigateToBookingDetail(page, 'BKG-0005');
    await navigateToRouteDesign(page, 'BKG-0005');

    // 調整前: 期限（2026-05-10）に対し V0001 到着（2026-05-14）で期限超過 ⚠。
    await expect(page.getByTestId('route-candidates')).toBeVisible();
    await expect(page.getByTestId('route-candidates')).toContainText('⚠');

    // 期限を 10 日延長して再算出。
    await page.fill('input[name="extend_deadline_days"]', '10');
    await page.getByTestId('route-adjust-submit').click();

    // 調整後: 期限内となり ⚠ が消える（候補は再提示される）。
    await expect(page.getByTestId('route-candidates')).toBeVisible();
    await expect(page.getByTestId('route-candidates')).not.toContainText('⚠');
    await expect(page.getByTestId('route-candidates')).toContainText('V0001');
  });

  test('US11: 経路設計画面から経路を確定すると経路提案中になる', async ({ page }) => {
    await login(page, 'designer');
    await navigateToBookingDetail(page, 'BKG-0004');
    await navigateToRouteDesign(page, 'BKG-0004');
    await expect(page.getByTestId('route-candidates')).toBeVisible();

    await page.getByTestId('route-confirm').click();
    await page.waitForURL('**/bookings/BKG-0004');
    expect(await bookingStatus(page)).toBe('ROUTE_PROPOSED');
    await expect(page.getByTestId('selected-route')).toBeVisible();
  });

  test('US13: 経路提案中の予約を経路設計中に差し戻せる', async ({ page }) => {
    // 直前の US11 テストで BKG-0004 は経路提案中になっている。
    await login(page, 'sales');
    await navigateToBookingDetail(page, 'BKG-0004');
    expect(await bookingStatus(page)).toBe('ROUTE_PROPOSED');

    await page.getByTestId('revert-btn').click();
    await page.waitForURL('**/bookings/BKG-0004');
    expect(await bookingStatus(page)).toBe('ROUTE_DESIGNING');
  });

  test('US12/US13: 荷主通知の後に予約を確定できる', async ({ page }) => {
    await login(page, 'sales');
    await navigateToBookingDetail(page, 'BKG-0002');
    expect(await bookingStatus(page)).toBe('ROUTE_PROPOSED');
    await expect(page.getByTestId('selected-route')).toBeVisible();

    // US12: 荷主通知（状態は経路提案中のまま）。
    await page.getByTestId('notify-route-btn').click();
    await page.waitForURL('**/bookings/BKG-0002');
    expect(await bookingStatus(page)).toBe('ROUTE_PROPOSED');

    // US13: 予約確定（経路提案中→予約確定）。
    await page.getByTestId('confirm-btn').click();
    await page.waitForURL('**/bookings/BKG-0002');
    expect(await bookingStatus(page)).toBe('CONFIRMED');
  });

  test('US13: 経路設計中の予約をキャンセルできる', async ({ page }) => {
    // BKG-0005 は US10 テストでも状態を変えず経路設計中のまま。
    await login(page, 'sales');
    await navigateToBookingDetail(page, 'BKG-0005');
    expect(await bookingStatus(page)).toBe('ROUTE_DESIGNING');

    await page.getByTestId('cancel-btn').click();
    await page.waitForURL('**/bookings/BKG-0005');
    expect(await bookingStatus(page)).toBe('CANCELLED');
  });
});
