import { test, expect } from '@playwright/test';
import {
  login,
  logout,
  navigateToBookingDetail,
  navigateToRouteDesign,
  bookingStatus,
  createBooking,
} from './helpers';

/**
 * IT3 デモ項目（経路算出・選択）の E2E テスト。ナビゲーション経由で操作する。
 *
 * 1. 経路設計画面で貨物仕様を確認し航海を検索する（US07）
 * 2. 経路候補が推奨順（直行便を最優先）に算出され、期限超過候補が警告される（US08）
 * 3. 候補を 1 件選択して経路を確定する（US09）
 *
 * 他イテレーションの予約と干渉しないよう、デモ用の予約を UI から自前で作成する。
 * 経路確定（US09）は現行実装で予約を経路提案中へ遷移させる（US11）ため、事前に
 * 営業担当者が経路設計依頼して経路設計中にしておく。
 */
test.describe.serial('IT3 デモ: 経路算出・選択', () => {
  test('US07/US08/US09: 貨物仕様確認→推奨順の候補→確定', async ({ page }) => {
    // 営業担当者: 期限内に到達できる予約を作成し経路設計を依頼する。
    await login(page, 'sales');
    const bookingId = await createBooking(page, {
      origin: 'JPOSA',
      destination: 'USLAX',
      deadline: '2026-05-20',
    });
    await navigateToBookingDetail(page, bookingId);
    await page.getByTestId('assign-routing-btn').click();
    await page.waitForURL(`**/bookings/${bookingId}`);
    await logout(page);

    // 経路設計者: 経路設計画面で貨物仕様・候補を確認する。
    await login(page, 'designer');
    await navigateToBookingDetail(page, bookingId);
    await navigateToRouteDesign(page, bookingId);

    // US07: 貨物仕様（出発地・目的地）が表示される。
    await expect(page.getByTestId('cargo-spec')).toContainText('JPOSA');
    await expect(page.getByTestId('cargo-spec')).toContainText('USLAX');

    // US08: 経路候補が推奨順（直行便を最優先=★1・直行）で提示される。
    await expect(page.getByTestId('route-candidates')).toBeVisible();
    await expect(page.getByTestId('route-candidates')).toContainText('★1');
    await expect(page.getByTestId('route-candidates')).toContainText('直行');
    await expect(page.getByTestId('route-candidates')).toContainText('V0001');

    // US09: 候補を確定すると経路提案中へ遷移する。
    await page.getByTestId('route-confirm').click();
    await page.waitForURL(`**/bookings/${bookingId}`);
    expect(await bookingStatus(page)).toBe('ROUTE_PROPOSED');
  });

  test('US08: 期限内に到達できない場合は期限超過候補が警告表示される', async ({ page }) => {
    // 営業担当者: 期限が早すぎる（航海到着より前）予約を作成し経路設計を依頼する。
    await login(page, 'sales');
    const bookingId = await createBooking(page, {
      origin: 'JPOSA',
      destination: 'USLAX',
      deadline: '2026-05-08',
    });
    await navigateToBookingDetail(page, bookingId);
    await page.getByTestId('assign-routing-btn').click();
    await page.waitForURL(`**/bookings/${bookingId}`);
    await logout(page);

    // 経路設計者: 候補は出るが期限超過（⚠）で警告される。
    await login(page, 'designer');
    await navigateToBookingDetail(page, bookingId);
    await navigateToRouteDesign(page, bookingId);
    await expect(page.getByTestId('route-candidates')).toBeVisible();
    await expect(page.getByTestId('route-candidates')).toContainText('⚠');
  });
});
