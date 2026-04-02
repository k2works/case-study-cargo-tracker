import { expect, test } from '../fixtures';
import { BookingPage } from '../pages/BookingPage';
import { HandlingPage } from '../pages/HandlingPage';
import { LoginPage } from '../pages/LoginPage';
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

test.describe.serial('E12: US10 荷役作業を記録する', () => {
  let bookingId = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '荷役テスト荷主',
      `handling-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '250',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(7),
      requestedDeliveryDate: futureDateStr(21),
    });
    bookingId = await bookingPage.extractBookingId();

    await context.close();
  });

  test('E12-1: 荷役作業を登録して一覧で確認できる', async ({ page, loggedIn }) => {
    const handlingPage = new HandlingPage(page);
    await handlingPage.register({
      bookingId,
      eventType: 'LOAD',
      locationCode: 'JPTYO',
      completionTime: futureDateTimeLocal(2),
      memo: '本船に積み込み',
    });

    await expect(page).toHaveURL(`/handling?bookingId=${bookingId}`);

    await handlingPage.searchByBookingId(bookingId);
    await handlingPage.expectEventListed({
      bookingId,
      eventType: 'LOAD',
      locationCode: 'JPTYO',
      completionDateTime: futureDateTimeLocal(2).replace('T', ' '),
      memo: '本船に積み込み',
    });
  });

  test('E12-2: 同一予約に複数の荷役イベントを記録できる', async ({ page, loggedIn }) => {
    const handlingPage = new HandlingPage(page);
    await handlingPage.register({
      bookingId,
      eventType: 'CUSTOMS',
      locationCode: 'JPTYO',
      completionTime: futureDateTimeLocal(4),
      memo: '通関完了',
    });

    await handlingPage.searchByBookingId(bookingId);
    await expect(page.locator('tbody tr').filter({ hasText: bookingId })).toHaveCount(2);
    await handlingPage.expectEventListed({
      bookingId,
      eventType: 'CUSTOMS',
      locationCode: 'JPTYO',
      completionDateTime: futureDateTimeLocal(4).replace('T', ' '),
      memo: '通関完了',
    });
  });

  test('E12-3: 荷役イベントを予約 ID で REST API 一覧取得できる', async ({ page, loggedIn }) => {
    const response = await page.request.get(`/api/v1/handling-events?bookingId=${bookingId}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveLength(2);
    expect(body[0].bookingId).toBe(bookingId);
    expect(body.map((event: { eventType: string }) => event.eventType)).toEqual(
      expect.arrayContaining(['LOAD', 'CUSTOMS']),
    );
  });

  test('E12-4: 必須フィールド未入力ではバリデーションエラーになる', async ({ page, loggedIn }) => {
    const handlingPage = new HandlingPage(page);
    await handlingPage.gotoNew();

    await page.locator('form[action="/handling"] button[type="submit"]').click();

    await expect(page).toHaveURL('/handling/new');
    await expect(page.locator('input[name="bookingId"]:invalid')).toHaveCount(1);
    await expect(page.locator('select[name="eventType"]:invalid')).toHaveCount(1);
    await expect(page.locator('input[name="locationCode"]:invalid')).toHaveCount(1);
    await expect(page.locator('input[name="completionTime"]:invalid')).toHaveCount(1);
  });

  test('E12-5: 存在しない予約 ID の登録は REST API で 404 を返す', async ({ page, loggedIn }) => {
    const unknownBookingId = '00000000-0000-0000-0000-000000000001';
    const handlingPage = new HandlingPage(page);
    await handlingPage.gotoNew();
    const csrfToken = await page
      .locator('form[action="/handling"] input[name="_csrf"]')
      .inputValue();

    const response = await page.request.post('/api/v1/handling-events', {
      headers: {
        'X-CSRF-TOKEN': csrfToken,
      },
      data: {
        bookingId: unknownBookingId,
        eventType: 'LOAD',
        locationCode: 'JPTYO',
        completionTime: futureDateTimeLocal(6) + ':00',
        memo: '存在しない予約',
      },
    });

    expect(response.status()).toBe(404);
  });
});
