import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { VoyagePage } from '../pages/VoyagePage';
import { RouteCandidatePage } from '../pages/RouteCandidatePage';

/** US09 経路選択・確定 E2E。
 *  voyage 登録 → 荷主登録 → 予約 → 経路設計引き渡し → 経路候補画面 → 「この経路で確定」
 *  → 予約状態が RouteAssigned に遷移し、紐付け経路が表示される。
 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

test.describe('US09 経路選択・確定', () => {
  test('候補画面から「この経路で確定」を押すと RouteAssigned に遷移し itinerary が紐付く', async ({
    page,
    loggedIn,
  }) => {
    const suffix = uniqueSuffix();

    // Given: 候補となる voyage を 1 件登録
    const voyage = new VoyagePage(page);
    await voyage.gotoNew();
    const voyageNumber = `VY-US09-${suffix}`;
    await voyage.fillRegister({
      voyageNumber,
      departureLocation: 'JPYOK',
      arrivalLocation: 'USNYC',
      departureTime: '2099-07-01T10:00',
      arrivalTime: '2099-07-10T18:00',
    });
    await voyage.submitRegister();

    // 荷主と予約を作成
    const shipper = new ShipperPage(page);
    await shipper.gotoNew();
    await shipper.fillForm({
      name: 'US09 テスト荷主',
      email: `us09-${suffix}@example.com`,
      phone: '03-0000-0000',
      address: '東京都',
      shipperType: 'Individual',
    });
    await shipper.submit();
    await shipper.gotoList();
    const shipperId = ((await page.locator('tr', { hasText: `us09-${suffix}@example.com` }).textContent()) ?? '').match(/SH-\S+/)?.[0]!;

    const booking = new BookingPage(page);
    await booking.gotoNew();
    await booking.fillForm({
      shipperCode: shipperId,
      origin: 'JPYOK',
      destination: 'USNYC',
      arrivalDeadline: futureDate(365 * 70),
      cargoType: 'General',
      weightKg: 500,
    });
    await booking.submit();
    const bookingId = page.url().split('/').pop()!;
    await booking.assignToRouting();

    // When: 経路候補画面から確定
    const routes = new RouteCandidatePage(page);
    await routes.gotoCandidates(bookingId);
    await expect(page.locator('body')).toContainText(voyageNumber);
    await routes.confirmCandidate(0);
    await expect(page).toHaveURL(`/bookings/${bookingId}/routes`);
    await expect(page.locator('.alert-success')).toContainText('経路を確定');

    // Then: 予約詳細で RouteAssigned + itinerary 表示
    await booking.gotoDetail(bookingId);
    await expect(page.locator('.badge')).toContainText('RouteAssigned');
    await expect(page.locator('body')).toContainText(voyageNumber);
  });
});
