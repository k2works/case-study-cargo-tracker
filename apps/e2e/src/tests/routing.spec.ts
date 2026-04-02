import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { RoutingPage } from '../pages/RoutingPage';

test.describe('E06: 最適ルート検索', () => {
  let shipperId: string;
  let bookingId: string;

  // 各テストの前に荷主と一般貨物の予約を登録する
  test.beforeEach(async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      'ルートテスト荷主',
      `route-test-${Date.now()}@example.com`,
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
      requestedPickupDate: '2026-05-01',
      requestedDeliveryDate: '2026-06-30',
    });
    bookingId = await bookingPage.extractBookingId();
  });

  test('予約詳細の「ルート検索」ボタンからルート検索ページに遷移できる', async ({ page }) => {
    await page.goto(`/bookings/${bookingId}`);

    const routeSearchLink = page.getByRole('link', { name: 'ルート検索' });
    await expect(routeSearchLink).toBeVisible();
    await routeSearchLink.click();

    await expect(page).toHaveURL(/\/routings\/search\?bookingId=/);
    await expect(page.locator('h4')).toContainText('ルート検索結果');
  });

  test('一般貨物の予約で検索するとルート候補が複数表示される', async ({ page }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    await expect(page.locator('h4')).toContainText('ルート検索結果');
    await expect(page.locator('h5')).toContainText('ルート候補');

    // SG001: 14 日, 150,000 円
    await routingPage.expectCandidateVisible({
      index: 0,
      voyageNumber: 'SG001',
      transitDaysText: '14 日',
      estimatedPriceText: '150,000 円',
    });

    // SG002: 18 日, 120,000 円
    await routingPage.expectCandidateVisible({
      index: 1,
      voyageNumber: 'SG002',
      transitDaysText: '18 日',
      estimatedPriceText: '120,000 円',
    });
  });

  test('ルート候補に経由港・推定着日・「間に合います」が表示される', async ({ page }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    const firstCard = routingPage.routeCandidateCard(0);
    await expect(firstCard).toContainText('SGSIN → JPTYO');
    await expect(firstCard.locator('.badge.bg-success')).toContainText('間に合います');
  });

  test('冷凍貨物の予約では対応ルートのみ表示される', async ({ page }) => {
    // 冷凍貨物で別の予約を作成（SG001 のみ対応）
    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'REFRIGERATED',
      weightKg: '300',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: '2026-05-01',
      requestedDeliveryDate: '2026-06-30',
    });
    const refrigeratedBookingId = await bookingPage.extractBookingId();

    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(refrigeratedBookingId);

    // SG001 のみ表示される（REFRIGERATED 対応）
    const count = await routingPage.countCandidates();
    expect(count).toBe(1);
    await routingPage.expectCandidateVisible({
      index: 0,
      voyageNumber: 'SG001',
      transitDaysText: '14 日',
      estimatedPriceText: '150,000 円',
    });
  });

  test('検索条件カードに出発地・目的地・希望着日が表示される', async ({ page }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    const conditionCard = page.locator('.card.shadow-sm.mb-4').first();
    await expect(conditionCard).toContainText('JPTYO');
    await expect(conditionCard).toContainText('SGSIN');
    await expect(conditionCard).toContainText('2026-06-30');
  });
});
