import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { VoyagePage } from '../pages/VoyagePage';
import { RouteCandidatePage } from '../pages/RouteCandidatePage';

/** US12 経路通知 + US13 予約確定 / 再設計 / キャンセル E2E。 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

async function bootedRouteAssigned(page: import('@playwright/test').Page): Promise<string> {
  const suffix = uniqueSuffix();
  const voyage = new VoyagePage(page);
  await voyage.gotoNew();
  await voyage.fillRegister({
    voyageNumber: `VY-LIFE-${suffix}`,
    departureLocation: 'JPYOK',
    arrivalLocation: 'USNYC',
    departureTime: '2099-07-01T10:00',
    arrivalTime: '2099-07-10T18:00',
  });
  await voyage.submitRegister();

  const shipper = new ShipperPage(page);
  await shipper.gotoNew();
  await shipper.fillForm({
    name: 'US13 テスト荷主',
    email: `us13-${suffix}@example.com`,
    phone: '03-0000-0000',
    address: '東京都',
    shipperType: 'Individual',
  });
  await shipper.submit();
  await shipper.gotoList();
  const shipperId = ((await page.locator('tr', { hasText: `us13-${suffix}@example.com` }).textContent()) ?? '').match(/SH-\S+/)?.[0]!;

  const booking = new BookingPage(page);
  await booking.gotoNew();
  await booking.fillForm({
    shipperCode: shipperId,
    origin: 'JPYOK',
    destination: 'USNYC',
    arrivalDeadline: "2099-12-31",
    cargoType: 'General',
    weightKg: 500,
  });
  await booking.submit();
  const bookingId = page.url().split('/').pop()!;
  await booking.assignToRouting();

  const routes = new RouteCandidatePage(page);
  await routes.gotoCandidates(bookingId);
  await routes.confirmCandidate(0);
  return bookingId;
}

test.describe('US12 経路通知', () => {
  test('ダッシュボードの RouteAssigned 一覧から「経路を通知」を実行すると通知ログに RouteNotified が記録される', async ({
    page,
    loggedIn,
  }) => {
    const bookingId = await bootedRouteAssigned(page);
    // 通知ボタンはダッシュボードの RouteAssigned 一覧にある
    await page.goto('/');
    await page
      .locator(`form[action="/bookings/${bookingId}/notify-route"] button[type="submit"]`)
      .click();
    await expect(page).toHaveURL(`/bookings/${bookingId}/notifications`);
    await expect(page.locator('body')).toContainText('RouteNotified');
  });
});

test.describe('US13 予約ライフサイクル', () => {
  test('RouteAssigned → 予約を確定 → Confirmed に遷移 + BookingConfirmed 通知ログ', async ({
    page,
    loggedIn,
  }) => {
    const bookingId = await bootedRouteAssigned(page);
    const booking = new BookingPage(page);
    await booking.gotoDetail(bookingId);

    await page.locator('button[data-action="confirm"]').click();
    await expect(page).toHaveURL(`/bookings/${bookingId}`);
    await expect(page.locator('.badge')).toContainText('Confirmed');

    await page.goto(`/bookings/${bookingId}/notifications`);
    await expect(page.locator('body')).toContainText('BookingConfirmed');
  });

  test('RouteAssigned → 経路再設計に戻す → RouteProposed に巻き戻り itinerary がリセット', async ({
    page,
    loggedIn,
  }) => {
    const bookingId = await bootedRouteAssigned(page);
    const booking = new BookingPage(page);
    await booking.gotoDetail(bookingId);

    await page.locator('button[data-action="repropose"]').click();
    await expect(page.locator('.badge')).toContainText('RouteProposed');
    await expect(page.locator('body')).not.toContainText('紐付け経路');
  });

  test('RouteAssigned → キャンセル → Cancelled + BookingCancelled 通知ログ', async ({
    page,
    loggedIn,
  }) => {
    const bookingId = await bootedRouteAssigned(page);
    const booking = new BookingPage(page);
    await booking.gotoDetail(bookingId);

    // 確認ダイアログを自動承認
    page.once('dialog', async (dialog) => {
      await dialog.accept();
    });
    await page.locator('button[data-action="cancel"]').click();
    await expect(page.locator('.badge')).toContainText('Cancelled');

    await page.goto(`/bookings/${bookingId}/notifications`);
    await expect(page.locator('body')).toContainText('BookingCancelled');
  });
});
