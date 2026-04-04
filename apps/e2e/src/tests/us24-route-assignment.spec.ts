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

// ─────────────────────────────────────────────────────────────────────────────
// E39〜E40: US24 経路情報を予約に紐付ける
//
// 受入条件:
//   AC1: 予約番号と確定経路を確認できる
//        — 予約詳細に予約番号が表示され「割り当て済みルート」カードが確認できる
//   AC2: 経路情報を予約に紐付ける操作ができる
//        — 割り当て後に「予約を確定する」ボタンが表示される
//   AC3: 紐付け後、経路情報と予約の紐付けが保存される
//        — 「割り当て済みルート」カードに航海番号・ルートパス・推定着日が表示される
//   AC4: 営業担当者に経路確定の通知が送信される
//   AC5: 荷主に確定経路の詳細（経由港・日程）が通知される
//        ※ AC4/AC5 はログ記録による通知。Playwright E2E ではログ出力を確認できないため、
//           US24E2ETest.java（Spring Boot LogAppender テスト）で保証済み。
// ─────────────────────────────────────────────────────────────────────────────
test.describe.serial('E39〜E40: US24 経路情報を予約に紐付ける', () => {
  let bookingId = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      'US24 経路紐付けテスト荷主',
      `us24-route-assign-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(1),
      requestedDeliveryDate: futureDateStr(6),
    });
    bookingId = await bookingPage.extractBookingId();

    await context.close();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E39: 紐付け前は「未割り当て」、予約番号が表示される（AC1 前提確認）
  // ─────────────────────────────────────────────────────────────────────────
  test('E39: 予約詳細ページに予約番号が表示され「割り当て済みルート」は未割り当て状態である', async ({
    page,
    loggedIn,
  }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.gotoDetail(bookingId);

    // AC1: 予約番号がページ見出しに表示される
    await expect(page.locator('h5 code')).toContainText(bookingId);

    // 紐付け前は「未割り当て」が表示される
    await expect(bookingPage.assignedRouteCard).toContainText('未割り当て');

    // 紐付け前は assignedRoute == null のため「予約を確定する」ボタンは非表示
    await expect(bookingPage.confirmButton).not.toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E40: 経路紐付け後に「割り当て済みルート」に全詳細が保存され「予約を確定する」ボタンが表示される
  //      （AC1 + AC2 + AC3）
  // ─────────────────────────────────────────────────────────────────────────
  test('E40: 経路を紐付けると「割り当て済みルート」に航海番号・ルートパス・推定着日が保存され「予約を確定する」ボタンが現れる', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // AC2: 経路情報を予約に紐付ける操作を実行（SG001 を割り当て）
    await routingPage.assignRoute(0);

    // 予約詳細ページにリダイレクトされる
    await expect(page).toHaveURL(`/bookings/${bookingId}`);

    const bookingPage = new BookingPage(page);

    // AC1: 予約番号が引き続き見出しに表示されている
    await expect(page.locator('h5 code')).toContainText(bookingId);

    // AC3: 「割り当て済みルート」カードに経路情報が保存・表示されている
    const routeCard = bookingPage.assignedRouteCard;
    await expect(routeCard).toContainText('SG001');             // 航海番号
    await expect(routeCard).toContainText('JPTYO');             // ルートパス（出発地）
    await expect(routeCard).toContainText('SGSIN');             // ルートパス（目的地）
    await expect(routeCard).toContainText(/\d{4}-\d{2}-\d{2}/); // 推定着日（YYYY-MM-DD）

    // AC2: 紐付け完了後、PROVISIONAL + assignedRoute != null → 「予約を確定する」ボタンが表示される
    await expect(bookingPage.confirmButton).toBeVisible();
  });
});
