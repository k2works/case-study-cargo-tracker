import { expect, test } from '../fixtures';
import { BookingPage } from '../pages/BookingPage';
import { HandlingPage } from '../pages/HandlingPage';
import { LoginPage } from '../pages/LoginPage';
import { RoutingPage } from '../pages/RoutingPage';
import { ShipperPage } from '../pages/ShipperPage';
import { TrackingPage } from '../pages/TrackingPage';

function futureDateStr(daysAhead: number): string {
  const date = new Date();
  date.setDate(date.getDate() + daysAhead);
  return date.toISOString().slice(0, 10);
}

function futureDateTimeLocal(hoursAhead: number): string {
  const date = new Date();
  date.setHours(date.getHours() + hoursAhead, 0, 0, 0);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:00`;
}

test.describe.serial('E13〜E15: US11 引取・US12 手動更新・US13 追跡照会', () => {
  let bookingId = '';
  let trackingNumber = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '追跡フローテスト荷主',
      `tracking-flow-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '180',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(7),
      requestedDeliveryDate: futureDateStr(21),
    });
    bookingId = await bookingPage.extractBookingId();

    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);
    await routingPage.assignRoute(0);

    await bookingPage.gotoDetail(bookingId);
    await bookingPage.confirmBooking();
    trackingNumber = await bookingPage.getTrackingNumber();

    await context.close();
  });

  test('E13: 引取を記録すると追跡履歴に表示され、重複登録はエラーになる', async ({
    page,
    loggedIn,
  }) => {
    const handlingPage = new HandlingPage(page);
    const trackingPage = new TrackingPage(page);

    await handlingPage.gotoReceive(bookingId);
    await expect(page.locator('h4')).toContainText('引取記録');

    await handlingPage.registerReceive({
      bookingId,
      locationCode: 'SGSIN',
      completionTime: futureDateTimeLocal(2),
      memo: '荷受人へ引き渡し完了',
    });

    await expect(page).toHaveURL(`/handling?bookingId=${bookingId}`);
    await trackingPage.goto(trackingNumber);
    await trackingPage.expectHistoryRow(0, {
      locationCode: 'SGSIN',
      eventType: '引取',
      memo: '荷受人へ引き渡し完了',
    });

    await handlingPage.registerReceive({
      bookingId,
      locationCode: 'SGSIN',
      completionTime: futureDateTimeLocal(3),
      memo: '重複引取',
    });

    await expect(page).toHaveURL(/\/handling\/receive/);
    await expect(page.locator('[data-testid="booking-id-error"]')).toContainText('引取');
  });

  test('E14: 手動更新はメモ必須で、登録後に追跡履歴へ反映される', async ({
    page,
    loggedIn,
  }) => {
    const handlingPage = new HandlingPage(page);
    const trackingPage = new TrackingPage(page);

    await handlingPage.gotoManualUpdate(bookingId);
    await expect(page.locator('h4')).toContainText('手動更新記録');

    await handlingPage.registerManualUpdate({
      bookingId,
      locationCode: 'SGSIN',
      completionTime: futureDateTimeLocal(4),
      memo: '',
    });

    await expect(page).toHaveURL(/\/handling\/manual-update/);
    await expect(page.locator('[data-testid="memo-error"]')).toContainText('メモ');

    await handlingPage.registerManualUpdate({
      bookingId,
      locationCode: 'SGSIN',
      completionTime: futureDateTimeLocal(5),
      memo: '台風のため一時保管',
    });

    await expect(page).toHaveURL(`/handling?bookingId=${bookingId}`);
    await trackingPage.goto(trackingNumber);
    await expect(trackingPage.historyRows()).toHaveCount(2);
    await trackingPage.expectHistoryRow(0, {
      locationCode: 'SGSIN',
      eventType: '手動更新',
      memo: '台風のため一時保管',
    });
    await trackingPage.expectHistoryRow(1, {
      locationCode: 'SGSIN',
      eventType: '引取',
      memo: '荷受人へ引き渡し完了',
    });
  });

  test('E15: 追跡番号リンクから認証なしで追跡情報を照会でき、未知の番号は 404 になる', async ({
    browser,
    page,
    loggedIn,
  }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.gotoDetail(bookingId);

    const trackingLink = page.locator(`a[href="/tracking/${trackingNumber}"]`);
    await expect(trackingLink).toBeVisible();

    const publicContext = await browser.newContext();
    const publicPage = await publicContext.newPage();
    const trackingPage = new TrackingPage(publicPage);

    await trackingPage.goto(trackingNumber);
    await expect(publicPage.locator('h2')).toContainText('追跡情報');
    await expect(publicPage.locator('[data-testid="booking-id"]')).toContainText(bookingId);
    await expect(await trackingPage.trackingNumberText()).toBe(trackingNumber);
    await expect(trackingPage.historyRows()).toHaveCount(2);
    await trackingPage.expectHistoryRow(0, {
      locationCode: 'SGSIN',
      eventType: '手動更新',
    });
    await trackingPage.expectHistoryRow(1, {
      locationCode: 'SGSIN',
      eventType: '引取',
    });

    await publicPage.goto('/tracking/TRK-UNKNOWN9');
    await expect(publicPage.locator('body')).toContainText('404');

    await publicContext.close();
  });
});
