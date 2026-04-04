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
// E30〜E32: US21 経路候補を算出する
//
// 受入条件:
//   AC1: 経路候補算出を実行すると、制約条件（航海スケジュール・寄港地接続・期限・
//        貨物種別・港湾制約）が自動チェックされる
//   AC2: 期限内に到着可能な経路候補が優先度順に一覧表示される
//   AC3: 各候補に経由港・所要日数・航海番号・費用概算が表示される
//   AC4: 危険物・冷凍貨物の場合、対応設備のある航海・港湾のみがフィルタリングされる
//   AC5: 制約条件を満たす経路候補がない場合、「条件を満たす経路候補なし」が表示される
//        ※ テスト環境では StubRouteProviderAdapter が使用され、同アダプタは希望着日
//           より 1〜2 日前に到着する候補を常に返す。日付フィルタが必ずパスするため、
//           全カーゴタイプで少なくとも 1 件の候補が返却される。通常の E2E フローでの
//           候補なし再現は不可能。候補なし UI は Thymeleaf テンプレート単体テストで保証。
// ─────────────────────────────────────────────────────────────────────────────
test.describe.serial('E30〜E32: US21 経路候補を算出する', () => {
  let bookingId = '';
  let deliveryDate = '';

  // 荷主と一般貨物予約を一度だけ作成する
  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '経路候補算出テスト荷主',
      `route-candidates-${Date.now()}@example.com`,
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
  // E30: 制約条件フィルタが適用され所要日数の少ない順に表示される（受入条件 1・2）
  // ─────────────────────────────────────────────────────────────────────────
  test('E30: 制約条件フィルタが適用され、期限内の経路候補が所要日数の少ない順に表示される', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // AC1: 「制約条件フィルタ適用済み」メッセージが表示される
    // （希望着日・貨物種別の制約条件が自動チェックされたことを示す）
    const filterMsg = page.locator('p.text-muted.small');
    await expect(filterMsg).toBeVisible();
    await expect(filterMsg).toContainText('制約条件フィルタ適用済み');
    await expect(filterMsg).toContainText(deliveryDate);
    await expect(filterMsg).toContainText('所要日数の少ない順');

    // AC2: 期限内の候補が所要日数の少ない順に並んでいる
    // SG001（14 日）→ SG002（18 日）の順で表示される
    const firstCard = routingPage.routeCandidateCard(0);
    const secondCard = routingPage.routeCandidateCard(1);
    await expect(firstCard).toContainText('14 日');
    await expect(secondCard).toContainText('18 日');

    const firstDays = parseInt(
      (await firstCard.locator('dd').filter({ hasText: /\d+ 日/ }).first().textContent()) ?? '0',
    );
    const secondDays = parseInt(
      (await secondCard.locator('dd').filter({ hasText: /\d+ 日/ }).first().textContent()) ?? '0',
    );
    expect(firstDays).toBeLessThan(secondDays);
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E31: 各候補の詳細情報が表示される（受入条件 3）
  // ─────────────────────────────────────────────────────────────────────────
  test('E31: 各候補に経由港・所要日数・航海番号・費用概算・出発予定日が表示される', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // SG001: 直行便 — 14 日・150,000 円
    await routingPage.expectCandidateVisible({
      index: 0,
      voyageNumber: 'SG001',
      transitDaysText: '14 日',
      estimatedPriceText: '150,000 円',
    });
    const sg001Card = routingPage.routeCandidateCard(0);
    // 経由港（スタブは全ロケードを含む: SGSIN → JPTYO）が表示される
    await expect(sg001Card).toContainText('SGSIN');
    // 出発予定日が YYYY-MM-DD 形式で表示される
    await expect(sg001Card).toContainText(/\d{4}-\d{2}-\d{2}/);

    // SG002: 釜山（KRPUS）経由 — 18 日・120,000 円
    await routingPage.expectCandidateVisible({
      index: 1,
      voyageNumber: 'SG002',
      transitDaysText: '18 日',
      estimatedPriceText: '120,000 円',
    });
    const sg002Card = routingPage.routeCandidateCard(1);
    // 経由港（KRPUS: 釜山）が表示される
    await expect(sg002Card).toContainText('KRPUS');
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E32: 危険物貨物では対応した航海のみが表示される（受入条件 4）
  // ─────────────────────────────────────────────────────────────────────────
  test('E32: 危険物貨物の場合、対応した航海（SG002）のみがフィルタリングされ表示される', async ({
    page,
    loggedIn,
  }) => {
    // HAZARDOUS 指定で直接 URL 検索
    // SG001 は GENERAL・REFRIGERATED のみ対応 → フィルタで除外
    // SG002 は GENERAL・HAZARDOUS 対応 → フィルタを通過
    await page.goto(
      `/routings/search?originLocode=JPTYO&destinationLocode=SGSIN` +
        `&requestedArrivalDate=${futureDateStr(6)}&cargoType=HAZARDOUS&weightKg=500`,
    );

    const routingPage = new RoutingPage(page);

    // SG002 のみ 1 件表示される
    const count = await routingPage.countCandidates();
    expect(count).toBe(1);

    await routingPage.expectCandidateVisible({
      index: 0,
      voyageNumber: 'SG002',
      transitDaysText: '18 日',
      estimatedPriceText: '120,000 円',
    });

    // 貨物種別フィルタのメッセージに「危険物」が表示される
    const filterMsg = page.locator('p.text-muted.small');
    await expect(filterMsg).toContainText('危険物');
  });
});
