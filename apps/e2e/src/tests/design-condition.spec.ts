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
// E26〜E27: US19 経路設計条件を確認する
//
// 受入条件:
//   AC1: 予約番号を指定して予約情報（出発地・目的地・期限・貨物種別・重量）を一覧表示できる
//   AC2: 経路設計条件（出発地・目的地・期限・貨物種別制約）を確認・記録できる
//   AC3: 予約情報に不備がある場合、営業担当者に条件補完を依頼できる
//        ※ REST API / Web フォームは全必須項目を強制するため、不完全な予約の
//           作成は通常フローでは不可能。条件分岐はテンプレート単体テストで保証済み。
//   AC4: 条件確認完了後、航海スケジュール検索（US20）に進める
// ─────────────────────────────────────────────────────────────────────────────
test.describe.serial('E26〜E27: US19 経路設計条件を確認する', () => {
  let bookingId = '';
  let requestedDeliveryDate = '';

  // 荷主と一般貨物予約を一度だけ作成する
  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '経路設計確認テスト荷主',
      `design-cond-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    requestedDeliveryDate = futureDateStr(3);

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(1),
      requestedDeliveryDate,
    });
    bookingId = await bookingPage.extractBookingId();

    await context.close();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E26: 経路設計条件の全フィールドを確認できる（受入条件 1・2）
  // ─────────────────────────────────────────────────────────────────────────
  test('E26: 予約番号を指定して経路設計条件の全フィールドを確認できる', async ({ page, loggedIn }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoDesignCondition(bookingId);

    // ページ見出し
    await expect(page.locator('h4')).toContainText('経路設計条件確認');

    // 経路設計条件カードに全フィールドが表示される（受入条件 1）
    const card = routingPage.conditionCard;
    await expect(card).toContainText(bookingId);       // 予約番号
    await expect(card).toContainText('JPTYO');          // 出発地 UN/LOCODE
    await expect(card).toContainText('SGSIN');          // 目的地 UN/LOCODE
    await expect(card).toContainText(requestedDeliveryDate); // 希望着日
    await expect(card).toContainText('一般貨物');        // 貨物種別
    await expect(card).toContainText('500');            // 重量

    // 条件が揃っているため警告は表示されない（受入条件 2: 完全な条件であることを確認）
    await expect(routingPage.incompleteAlert).not.toBeVisible();

    // 「航海スケジュール検索へ進む」ボタンが表示される（受入条件 4 への入口）
    await expect(routingPage.searchFromConditionLink).toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E27: 「航海スケジュール検索へ進む」で US20 に遷移できる（受入条件 4）
  // ─────────────────────────────────────────────────────────────────────────
  test('E27: 条件確認完了後に「航海スケジュール検索へ進む」で検索ページに進める', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoDesignCondition(bookingId);

    await routingPage.searchFromConditionLink.click();

    // 航海スケジュール検索ページ（US20）に遷移できる
    await expect(page).toHaveURL(
      new RegExp(`/routings/search\\?bookingId=${bookingId}`),
    );
    await expect(page.locator('h4')).toContainText('ルート検索結果');
  });
});
