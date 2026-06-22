import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';

/** US06: 経路設計者への引き渡しフロー E2E。
 *  予約作成 → 予約詳細から引き渡し → ダッシュボードの引き渡し済み一覧に表示。
 *  admin は全ロール保持のため、ダッシュボードに RouteDesigner 用カードと一覧が表示される。
 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

test.describe('US06 経路設計者への引き渡し', () => {
  test('予約作成 → 引き渡し → ダッシュボードに表示される', async ({ page, loggedIn }) => {
    // Given: 荷主と予約を作成
    const shipper = new ShipperPage(page);
    await shipper.gotoNew();
    const shipperCode = uniqueSuffix();
    await shipper.fillForm({
      name: 'US06 テスト荷主',
      email: `us06-${shipperCode}@example.com`,
      phone: '03-0000-0000',
      address: '東京都',
      shipperType: 'Individual',
    });
    await shipper.submit();
    await shipper.gotoList();
    const row = page.locator('tr', { hasText: `us06-${shipperCode}@example.com` });
    const rowText = (await row.textContent()) ?? '';
    const shipperId = rowText.match(/SH-\S+/)?.[0];
    expect(shipperId).toBeTruthy();

    const booking = new BookingPage(page);
    await booking.gotoNew();
    await booking.fillForm({
      shipperCode: shipperId!,
      origin: 'JPYOK',
      destination: 'USNYC',
      arrivalDeadline: futureDate(30),
      cargoType: 'General',
      weightKg: 500,
      description: 'US06 引き渡しテスト',
      quantity: 1,
    });
    await booking.submit();
    await expect(page).toHaveURL(/\/bookings\/[^/]+$/);
    const bookingId = page.url().split('/').pop()!;

    // When: 予約詳細から経路設計者へ引き渡し
    await booking.assignToRouting();
    await expect(page).toHaveURL(`/bookings/${bookingId}`);
    await expect(page.locator('.alert-success')).toContainText('経路設計者へ引き渡し');
    await expect(page.locator('.badge')).toContainText('RouteProposed');

    // Then: ダッシュボードの RouteProposed 一覧に表示される（「貨物種別」列で RouteAssigned 一覧と区別）
    await page.goto('/');
    await expect(page.locator('#route-proposed')).toBeVisible();
    const routeProposedTable = page.locator('table:has(th:has-text("貨物種別"))');
    await expect(routeProposedTable).toContainText(bookingId);
    await expect(routeProposedTable).toContainText('JPYOK');
  });

  test('既に RouteProposed の予約は引き渡しボタンが非表示', async ({ page, loggedIn }) => {
    // 直前テストで作成した予約は次回 cleanup に依存しないが、各テスト独立性のため新規に作成
    const shipper = new ShipperPage(page);
    await shipper.gotoNew();
    const suffix = uniqueSuffix();
    await shipper.fillForm({
      name: 'US06 ボタン非表示テスト',
      email: `us06-btn-${suffix}@example.com`,
      phone: '03-0000-0000',
      address: '東京都',
      shipperType: 'Individual',
    });
    await shipper.submit();
    await shipper.gotoList();
    const row = page.locator('tr', { hasText: `us06-btn-${suffix}@example.com` });
    const shipperId = ((await row.textContent()) ?? '').match(/SH-\S+/)?.[0];

    const booking = new BookingPage(page);
    await booking.gotoNew();
    await booking.fillForm({
      shipperCode: shipperId!,
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: futureDate(20),
      cargoType: 'General',
      weightKg: 300,
    });
    await booking.submit();
    const bookingId = page.url().split('/').pop()!;
    await booking.assignToRouting();

    // RouteProposed 後に詳細を再表示
    await booking.gotoDetail(bookingId);
    await expect(page.locator('button:has-text("経路設計者へ引き渡す")')).toHaveCount(0);
  });
});
