import { expect, test } from '../fixtures';
import { BookingPage } from '../pages/BookingPage';
import { ExceptionPage } from '../pages/ExceptionPage';
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

test.describe.serial('E16: US14 遅延例外を処理する', () => {
  let bookingId = '';
  let trackingNumber = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '遅延例外テスト荷主',
      `exception-flow-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '220',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(5),
      requestedDeliveryDate: futureDateStr(18),
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

  test('E16: 遅延例外を記録すると例外発生になり、通知付きの例外履歴を照会できる', async ({
    page,
    loggedIn,
  }) => {
    const exceptionPage = new ExceptionPage(page);
    const trackingPage = new TrackingPage(page);

    await exceptionPage.gotoNew(trackingNumber);
    await expect(page.locator('h4')).toContainText('例外事象記録');
    await expect(page.locator('[data-testid="exception-resolution"]')).toBeVisible();

    await exceptionPage.register({
      trackingNumber,
      exceptionType: 'DELAY',
      locationCode: 'SGSIN',
      occurredAt: futureDateTimeLocal(6),
      reason: '悪天候による港湾閉鎖',
      resolution: '',
    });

    await expect(page).toHaveURL(/\/exceptions\/new/);
    await expect(page.locator('textarea[name="resolution"]:invalid')).toHaveCount(1);

    await exceptionPage.register({
      trackingNumber,
      exceptionType: 'DELAY',
      locationCode: 'SGSIN',
      occurredAt: futureDateTimeLocal(7),
      reason: '悪天候による港湾閉鎖',
      resolution: '代替船を手配し、到着予定を 2026/06/05 に更新',
    });

    await expect(page).toHaveURL('/exceptions/new');
    await expect(page.locator('.alert-success')).toContainText('荷主へ通知しました');

    await trackingPage.goto(trackingNumber);
    await expect(page.locator('h2')).toContainText('追跡情報');
    await expect(trackingPage.currentState()).toHaveText('例外発生');
    await expect(page.locator('[data-testid="current-location"]')).toHaveText('SGSIN');
    await trackingPage.expectHistoryEmpty();
    await expect(trackingPage.exceptionRows()).toHaveCount(1);
    await trackingPage.expectExceptionRow(0, {
      locationCode: 'SGSIN',
      exceptionType: '遅延',
      reason: '悪天候による港湾閉鎖',
      resolution: '代替船を手配し、到着予定を 2026/06/05 に更新',
      shipperNotificationStatus: '通知済み',
    });
  });
});
