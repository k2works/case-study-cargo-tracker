import { expect, test } from '../fixtures';
import { BookingPage } from '../pages/BookingPage';
import { ExceptionPage } from '../pages/ExceptionPage';
import { FreightPage } from '../pages/FreightPage';
import { HandlingPage } from '../pages/HandlingPage';
import { LoginPage } from '../pages/LoginPage';
import { RoutingPage } from '../pages/RoutingPage';
import { ShipperPage } from '../pages/ShipperPage';

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

test.describe.serial('E19〜E20: US16 輸送料金を算出する', () => {
  let bookingId = '';
  let trackingNumber = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '料金算出テスト荷主',
      `freight-flow-${Date.now()}@example.com`,
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
      requestedPickupDate: futureDateStr(4),
      requestedDeliveryDate: futureDateStr(16),
    });
    bookingId = await bookingPage.extractBookingId();

    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);
    await routingPage.assignRoute(0);

    await bookingPage.gotoDetail(bookingId);
    await bookingPage.confirmBooking();
    trackingNumber = await bookingPage.getTrackingNumber();

    const handlingPage = new HandlingPage(page);
    await handlingPage.registerReceive({
      bookingId,
      locationCode: 'SGSIN',
      completionTime: futureDateTimeLocal(2),
      receiveConfirmationCode: 'RC-US16-001',
      memo: '配送完了',
    });

    await context.close();
  });

  test('E19: 引取済み予約から輸送実績を確認して料金算出し、確定できる', async ({
    page,
    loggedIn,
  }) => {
    const freightPage = new FreightPage(page);

    await freightPage.gotoCalculate(bookingId);
    await expect(page.locator('h4')).toContainText('輸送料金算出');
    await freightPage.expectSummary({
      routePath: 'JPTYO',
      distanceKm: '5,300',
      weightKg: '180',
      cargoType: 'GENERAL_CARGO',
      handlingCount: '1',
    });

    await freightPage.calculate({ bookingId });
    await expect(page).toHaveURL('/freight');
    await freightPage.expectChargeRow({
      bookingId,
      status: '算出中',
      baseAmount: '180',
      adjustmentAmount: '0',
      totalAmount: '180',
    });

    await freightPage.confirmByBookingId(bookingId);
    await expect(page).toHaveURL('/freight');
    await freightPage.expectChargeRow({
      bookingId,
      status: '確定',
      baseAmount: '180',
      adjustmentAmount: '0',
      totalAmount: '180',
    });
  });

  test('E20: 例外発生時は料金調整額を入力して算出できる', async ({
    page,
    loggedIn,
  }) => {
    const exceptionPage = new ExceptionPage(page);
    const freightPage = new FreightPage(page);

    await exceptionPage.register({
      trackingNumber,
      exceptionType: 'DAMAGE',
      locationCode: 'SGSIN',
      occurredAt: futureDateTimeLocal(3),
      reason: '外装破損',
      resolution: '補償対応を開始',
    });

    await expect(page.locator('.alert-success')).toContainText('荷主への通知を手動で行ってください');

    await freightPage.gotoCalculate(bookingId);
    await expect(page.locator('[data-testid="freight-adjustment-amount"]')).toBeVisible();
    await freightPage.calculate({
      bookingId,
      adjustmentAmount: '-30',
    });

    await expect(page).toHaveURL('/freight');
    await freightPage.expectChargeRow({
      bookingId,
      status: '算出中',
      baseAmount: '180',
      adjustmentAmount: '-30',
      totalAmount: '150',
    });
  });
});
