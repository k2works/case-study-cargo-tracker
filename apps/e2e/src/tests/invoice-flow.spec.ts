import { expect, test } from '../fixtures';
import { BookingPage } from '../pages/BookingPage';
import { FreightPage } from '../pages/FreightPage';
import { InvoicePage } from '../pages/InvoicePage';
import { HandlingPage } from '../pages/HandlingPage';
import { LoginPage } from '../pages/LoginPage';
import { RoutingPage } from '../pages/RoutingPage';
import { ShipperPage } from '../pages/ShipperPage';

function futureDateStr(daysAhead: number): string {
  const date = new Date();
  date.setDate(date.getDate() + daysAhead);
  return date.toISOString().slice(0, 10);
}

function relativeDateStr(daysOffset: number): string {
  const date = new Date();
  date.setDate(date.getDate() + daysOffset);
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

test.describe.serial('E23〜E25: US18 精算を処理する', () => {
  test.setTimeout(120_000);
  let settledBookingId = '';
  let overdueBookingId = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    const bookingPage = new BookingPage(page);
    const routingPage = new RoutingPage(page);
    const handlingPage = new HandlingPage(page);

    await shipperPage.registerIndividual(
      '精算テスト荷主',
      `invoice-flow-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    const prepareBooking = async (receiveCode: string) => {
      await bookingPage.register({
        shipperId,
        cargoType: 'GENERAL_CARGO',
        weightKg: '200',
        quantity: '1',
        originLocation: 'JPTYO',
        destinationLocation: 'SGSIN',
        requestedPickupDate: futureDateStr(4),
        requestedDeliveryDate: futureDateStr(14),
      });
      const bookingId = await bookingPage.extractBookingId();

      await routingPage.gotoByBookingId(bookingId);
      await routingPage.assignRoute(0);

      await bookingPage.gotoDetail(bookingId);
      await bookingPage.confirmBooking();

      await handlingPage.registerReceive({
        bookingId,
        locationCode: 'SGSIN',
        completionTime: futureDateTimeLocal(2),
        receiveConfirmationCode: receiveCode,
        memo: '精算対象の配送完了',
      });

      return bookingId;
    };

    settledBookingId = await prepareBooking('RC-US18-001');
    overdueBookingId = await prepareBooking('RC-US18-002');

    const freightPage = new FreightPage(page);

    await freightPage.calculate({ bookingId: settledBookingId });
    await freightPage.confirmByBookingId(settledBookingId);

    await freightPage.calculate({ bookingId: overdueBookingId });
    await freightPage.confirmByBookingId(overdueBookingId);

    await context.close();
  });

  test('E23: 確定した輸送料金から請求番号・請求金額・支払期限付きの精算書を発行し、荷主通知を表示する', async ({
    page,
    loggedIn,
  }) => {
    const freightPage = new FreightPage(page);
    const invoicePage = new InvoicePage(page);
    const dueDate = futureDateStr(30);

    await freightPage.gotoList();
    await freightPage.generateInvoiceByBookingId(settledBookingId, dueDate);
    await expect(page).toHaveURL('/invoices');
    await expect(page.locator('.alert-success')).toContainText('荷主へメール通知しました');

    await invoicePage.expectLatestInvoiceRow({
      bookingId: settledBookingId,
      amount: '200',
      dueDate,
      paymentStatus: '支払い待ち',
    });
    await invoicePage.openLatestInvoiceByBookingId(settledBookingId);
    await invoicePage.expectInvoiceDetail({
      bookingId: settledBookingId,
      amount: '200',
      dueDate,
      paymentStatus: '支払い待ち',
    });
  });

  test('E24: 決済機関の入金確認後に精算状態と予約状態が精算済みへ更新される', async ({
    page,
    loggedIn,
  }) => {
    const invoicePage = new InvoicePage(page);
    const bookingPage = new BookingPage(page);

    await invoicePage.gotoList();
    await invoicePage.confirmPaymentByBookingId(settledBookingId);
    await expect(page.locator('.alert-success')).toContainText('入金を確認し、精算を完了しました');
    await invoicePage.expectLatestInvoiceRow({
      bookingId: settledBookingId,
      amount: '200',
      paymentStatus: '支払い済み',
    });

    await bookingPage.gotoDetail(settledBookingId);
    await bookingPage.expectStatus('精算済');
  });

  test('E25: 支払い期限を超過した未払い精算書は経理担当者向け通知で確認できる', async ({
    page,
    loggedIn,
  }) => {
    const freightPage = new FreightPage(page);
    const invoicePage = new InvoicePage(page);
    const overdueDate = relativeDateStr(-1);

    await freightPage.gotoList();
    await freightPage.generateInvoiceByBookingId(overdueBookingId, overdueDate);
    await expect(page).toHaveURL('/invoices');
    await expect(page.locator('[data-testid="invoice-overdue-alert"]')).toContainText('未払い通知');

    await invoicePage.expectLatestInvoiceRow({
      bookingId: overdueBookingId,
      amount: '200',
      dueDate: overdueDate,
      paymentStatus: '支払い期限超過',
    });
  });
});
