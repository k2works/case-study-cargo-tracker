import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { RoutingPage } from '../pages/RoutingPage';
import { LoginPage } from '../pages/LoginPage';

function futureDateStr(monthsAhead: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + monthsAhead);
  return d.toISOString().slice(0, 10);
}

// E09〜E11 はシナリオが連続するため serial で実行順序を保証する
// beforeAll で荷主・予約を作成し、各テストで状態を積み重ねる
test.describe.serial('E09〜E11: ルート割り当て・予約確定・追跡番号発行', () => {
  let shipperId = '';
  let bookingId = '';

  // 荷主と PROVISIONAL 予約を一度だけ作成する
  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      'ルート確定テスト荷主',
      `route-confirm-${Date.now()}@example.com`,
    );
    shipperId = await shipperPage.extractShipperId();

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(1),
      requestedDeliveryDate: futureDateStr(3),
    });
    bookingId = await bookingPage.extractBookingId();

    await context.close();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E09: ルート割り当て（US07 受入条件）
  // ─────────────────────────────────────────────────────────────────────────
  test('E09: ルートを割り当てると予約詳細に航海番号が表示される', async ({ page, loggedIn }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // ルート候補が表示されている（modal-title の h5 と重複しないよう main 内に限定）
    await expect(page.locator('main h5')).toContainText('ルート候補');
    const count = await routingPage.countCandidates();
    expect(count).toBeGreaterThan(0);

    // 最初の候補を割り当てる
    await routingPage.assignRoute(0);

    // 予約詳細ページにリダイレクトされ、割り当てルート（SG001）が表示される
    await expect(page).toHaveURL(`/bookings/${bookingId}`);
    await expect(page.locator('body')).toContainText('SG001');
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E10: 予約確定（US08 受入条件）
  // ─────────────────────────────────────────────────────────────────────────
  test('E10: ルート割り当て済み予約を確定するとステータスが CONFIRMED になる', async ({
    page,
    loggedIn,
  }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.gotoDetail(bookingId);

    // PROVISIONAL 状態では「予約を確定する」ボタンが表示されている
    const confirmButton = page.locator('button:has-text("予約を確定する")');
    await expect(confirmButton).toBeVisible();

    // 確定ボタンをクリック
    await bookingPage.confirmBooking();

    // 予約詳細にリダイレクトされ、CONFIRMED バッジが表示される
    await expect(page).toHaveURL(`/bookings/${bookingId}`);
    await expect(page.locator('.badge.bg-success')).toContainText('確定済');

    // 確定済みの予約には「予約を確定する」ボタンが表示されない
    await expect(confirmButton).not.toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E11: 追跡番号発行（US09 受入条件）
  // ─────────────────────────────────────────────────────────────────────────
  test('E11: 予約確定後に TRK-XXXXXXXX 形式の追跡番号が発行される', async ({
    page,
    loggedIn,
  }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.gotoDetail(bookingId);

    // 追跡番号が表示されている
    const trackingNumber = await bookingPage.getTrackingNumber();
    expect(trackingNumber).toMatch(/^TRK-[A-Z0-9]{8}$/);
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E11b: REST API で追跡番号検索（US09 受入条件）
  // ─────────────────────────────────────────────────────────────────────────
  test('E11b: REST API で追跡番号を検索できる', async ({ page, loggedIn }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.gotoDetail(bookingId);
    const trackingNumber = await bookingPage.getTrackingNumber();

    // GET /api/v1/tracking/{trackingNumber} が 200 を返す
    const response = await page.request.get(`/api/v1/tracking/${trackingNumber}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body.trackingNumber).toBe(trackingNumber);
    expect(body.bookingId).toBe(bookingId);
  });

  test('E11c: 存在しない追跡番号は 404 を返す', async ({ page, loggedIn }) => {
    const response = await page.request.get('/api/v1/tracking/TRK-00000000');
    expect(response.status()).toBe(404);
  });
});
