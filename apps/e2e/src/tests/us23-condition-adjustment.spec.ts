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
// E36〜E38: US23 経路条件を調整して再算出する
//
// 受入条件:
//   AC1: 現在の制約条件を確認・表示できる
//        — 検索条件カードに出発地・目的地・希望着日・貨物種別・重量が表示される
//   AC2: 期限延長・経由地変更等の条件を調整できる
//   AC3: 調整後、経路候補の再算出（US21）が自動実行される
//        — 貨物種別を HAZARDOUS に変更して再検索すると SG002 のみ表示される
//   AC4: 調整可能な条件がない場合、営業担当者に荷主との条件交渉を依頼できる
//        ※ テスト環境では StubRouteProviderAdapter が使用され、常に候補が返却されるため、
//           候補なし状態（AC4 のトリガー）を Playwright E2E で再現できない。
//           候補なし UI・交渉依頼カードの表示は US23E2ETest.java（Spring Boot）で保証済み。
// ─────────────────────────────────────────────────────────────────────────────
test.describe.serial('E36〜E38: US23 経路条件を調整して再算出する', () => {
  let bookingId = '';
  let deliveryDate = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      'US23 条件調整テスト荷主',
      `us23-condition-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    deliveryDate = futureDateStr(6);

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(1),
      requestedDeliveryDate: deliveryDate,
    });
    bookingId = await bookingPage.extractBookingId();

    await context.close();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E36: 検索条件カードに現在の制約条件が表示される（AC1）
  // ─────────────────────────────────────────────────────────────────────────
  test('E36: 現在の制約条件（出発地・目的地・希望着日・貨物種別・重量）が検索条件カードに表示される', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // AC1: 検索条件カードに全制約条件が表示される
    const condCard = routingPage.conditionCard;
    await expect(condCard).toBeVisible();
    await expect(condCard).toContainText('JPTYO');      // 出発地
    await expect(condCard).toContainText('SGSIN');      // 目的地
    await expect(condCard).toContainText(deliveryDate); // 希望着日（予約の requestedDeliveryDate）
    await expect(condCard).toContainText('一般貨物');   // 貨物種別（GENERAL_CARGO → GENERAL）
    await expect(condCard).toContainText('500');        // 重量 (kg)
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E37: 貨物種別を変更して再検索すると候補が更新される（AC2 + AC3）
  //
  // 「条件を変更して再検索」フォームは候補なし時のみ表示される（Thymeleaf の
  // th:if="${#lists.isEmpty(candidates)}" ブロック内）。テスト環境ではスタブが
  // 常に候補を返すため、フォーム送信と同等のパラメータ変更を URL 直接指定で検証する。
  // ─────────────────────────────────────────────────────────────────────────
  test('E37: 貨物種別を HAZARDOUS に変更して再検索すると対応した候補（SG002）のみが表示される', async ({
    page,
    loggedIn,
  }) => {
    // 貨物種別を危険物（HAZARDOUS）に変更して検索（条件調整フォームの送信と同等）
    await page.goto(
      `/routings/search?originLocode=JPTYO&destinationLocode=SGSIN` +
        `&requestedArrivalDate=${deliveryDate}&cargoType=HAZARDOUS&weightKg=500&bookingId=${bookingId}`,
    );

    const routingPage = new RoutingPage(page);

    // AC3: 再算出が実行され、危険物対応の SG002 のみ表示される（1 件）
    const count = await routingPage.countCandidates();
    expect(count).toBe(1);

    await routingPage.expectCandidateVisible({
      index: 0,
      voyageNumber: 'SG002',
      transitDaysText: '18 日',
      estimatedPriceText: '120,000 円',
    });

    // AC2: 変更後の制約条件カードに「危険物」が反映されている
    await expect(routingPage.conditionCard).toContainText('危険物');

    // フィルタメッセージにも「危険物」が表示される
    const filterMsg = page.locator('p.text-muted.small');
    await expect(filterMsg).toContainText('危険物');

    // SG001 は危険物非対応のため除外されている
    await expect(page.locator('.badge.bg-primary', { hasText: 'SG001' })).not.toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E38: 候補がある場合は「営業担当者に条件交渉を依頼」カードが表示されない（AC4 前提確認）
  //
  // NOTE: 候補なし + bookingId あり の完全な AC4 シナリオ（交渉依頼カード表示・
  //       リンクの動作確認）は US23E2ETest.java（Spring Boot MockMvc）で保証済み。
  //       テスト環境の StubRouteProviderAdapter は常に候補を返すため、
  //       Playwright E2E での候補なし状態再現は不可能。
  // ─────────────────────────────────────────────────────────────────────────
  test('E38: 候補が見つかっている場合は「営業担当者に条件交渉を依頼」カードが表示されない', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // 候補が 1 件以上存在する（no-candidates ブロックが非表示の前提）
    expect(await routingPage.countCandidates()).toBeGreaterThan(0);

    // 候補がある場合、交渉依頼カードは表示されない（th:if="${#lists.isEmpty(candidates)}" ブロック内）
    await expect(routingPage.negotiationRequestCard).not.toBeVisible();
  });
});
