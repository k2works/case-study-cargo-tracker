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

  test('登録済み荷主を選択して貨物予約を登録でき、一覧で確認できる', async ({ page }) => {
    const bookingPage = new BookingPage(page);
    const bookingData = {
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500',
      quantity: '3',
      originLocation: 'JPTYO',
      destinationLocation: 'USNYC',
      requestedPickupDate: '2025-09-01',
      requestedDeliveryDate: '2025-10-15',
      description: '電子機器',
    } as const;

    // 予約登録フォームに遷移する
    await bookingPage.goto();

    // フォームが表示されることを確認する
    await expect(page.locator('h4')).toContainText('予約登録');
    await expect(page.locator('select[name="shipperId"]')).toBeVisible();
    await expect(page.locator('select[name="shipperId"] option')).toContainText([
      `予約テスト荷主 / booking-test-`,
    ]);

    // 予約を登録する
    await bookingPage.register(bookingData);

    // 予約詳細ページ（/bookings/{uuid}）にリダイレクトされる
    await expect(page).toHaveURL(/\/bookings\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/);

    // 予約番号が表示される
    await expect(page.locator('h5 code')).toBeVisible();

    // 登録成功メッセージが表示される
    await expect(page.locator('.alert-success')).toContainText('予約を登録しました');
    await expect(page.locator('dd').filter({ hasText: '予約テスト荷主' })).toBeVisible();

    const bookingId = await bookingPage.extractBookingId();

    // 予約一覧で登録内容を確認できる
    await bookingPage.gotoList();
    await expect(page).toHaveURL('/bookings');
    await bookingPage.expectBookingListed({
      bookingId,
      shipperName: '予約テスト荷主',
      cargoType: 'GENERAL_CARGO',
      originLocation: 'JPTYO',
      destinationLocation: 'USNYC',
      requestedPickupDate: '2025-09-01',
      status: 'PROVISIONAL',
    });
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

  test('予約詳細から見積登録へ遷移すると条件が初期入力される', async ({ page }) => {
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

    const quoteLink = page.getByRole('link', { name: 'この条件で見積を作成' });
    await expect(quoteLink).toBeVisible();
    await quoteLink.click();

    await expect(page).toHaveURL(/\/quotes\/new/);
    await expect(page.locator('h4')).toContainText('見積登録');
    await expect(page.locator('input[name="originLocode"]')).toHaveValue('JPOSA');
    await expect(page.locator('input[name="destinationLocode"]')).toHaveValue('DEHAM');
    await expect(page.locator('input[name="requestedArrivalDate"]')).toHaveValue('2025-11-01');
    await expect(page.locator('select[name="cargoType"]')).toHaveValue('REFRIGERATED');
    await expect(page.locator('input[name="weightKg"]')).toHaveValue(/200(?:\.0+)?/);
  });
});
