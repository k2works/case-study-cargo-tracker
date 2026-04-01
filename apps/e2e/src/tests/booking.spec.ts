import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';

test.describe('E04: 貨物予約登録', () => {
  let shipperId: string;

  // 各テストの前に荷主を登録してシッパー ID を取得する
  test.beforeEach(async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '予約テスト荷主',
      `booking-test-${Date.now()}@example.com`,
    );
    shipperId = await shipperPage.extractShipperId();
  });

  test('登録済み荷主を選択して貨物予約を登録できる', async ({ page }) => {
    const bookingPage = new BookingPage(page);

    // 予約登録フォームに遷移する
    await bookingPage.goto();

    // フォームが表示されることを確認する
    await expect(page.locator('h4')).toContainText('予約登録');

    // 予約を登録する
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500',
      quantity: '3',
      originLocation: 'JPTYO',
      destinationLocation: 'USNYC',
      requestedPickupDate: '2025-09-01',
      requestedDeliveryDate: '2025-10-15',
      description: '電子機器',
    });

    // 予約詳細ページ（/bookings/{uuid}）にリダイレクトされる
    await expect(page).toHaveURL(/\/bookings\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/);

    // 予約番号が表示される
    await expect(page.locator('h5 code')).toBeVisible();

    // 登録成功メッセージが表示される
    await expect(page.locator('.alert-success')).toContainText('予約を登録しました');
  });

  test('予約番号が発行される', async ({ page }) => {
    const bookingPage = new BookingPage(page);

    await bookingPage.register({
      shipperId,
      cargoType: 'REFRIGERATED',
      weightKg: '200',
      quantity: '1',
      originLocation: 'JPOSA',
      destinationLocation: 'DEHAM',
      requestedPickupDate: '2025-10-01',
      requestedDeliveryDate: '2025-11-01',
    });

    // 詳細ページの URL に UUID 形式の予約番号が含まれる
    const url = page.url();
    const uuidPattern = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/;
    expect(uuidPattern.test(url)).toBeTruthy();

    // 詳細ページに予約番号（UUID）が表示される
    const bookingIdCode = page.locator('h5 code');
    await expect(bookingIdCode).toBeVisible();
    const bookingIdText = await bookingIdCode.innerText();
    expect(uuidPattern.test(bookingIdText)).toBeTruthy();
  });
});
