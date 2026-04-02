import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';

function futureDateStr(monthsAhead: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + monthsAhead);
  return d.toISOString().slice(0, 10);
}

// ─────────────────────────────────────────────────────────────────────────────
// E07: 危険物予約（US05 受入条件）
// ─────────────────────────────────────────────────────────────────────────────
test.describe('E07: 危険物予約', () => {
  let shipperId: string;

  test.beforeEach(async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '危険物テスト荷主',
      `hazmat-${Date.now()}@example.com`,
    );
    shipperId = await shipperPage.extractShipperId();
  });

  test('UN 番号を入力して危険物予約を登録できる', async ({ page }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'DANGEROUS_GOODS',
      weightKg: '200',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'USNYC',
      requestedPickupDate: futureDateStr(1),
      requestedDeliveryDate: futureDateStr(4),
      unNumber: 'UN1234',
      hazardClass: 'クラス3',
    });

    // 予約詳細ページにリダイレクトされる
    await expect(page).toHaveURL(
      /\/bookings\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/,
    );
    await expect(page.locator('.alert-success')).toContainText('予約を登録しました');

    // 予約一覧でも確認できる
    const bookingId = await bookingPage.extractBookingId();
    await bookingPage.gotoList();
    await bookingPage.expectBookingListed({
      bookingId,
      shipperName: '危険物テスト荷主',
      cargoType: 'DANGEROUS_GOODS',
      originLocation: 'JPTYO',
      destinationLocation: 'USNYC',
      requestedPickupDate: futureDateStr(1),
      status: 'PROVISIONAL',
    });
  });

  test('UN 番号を入力しないと危険物予約は登録できない', async ({ page }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.goto();

    await page.locator('select[name="shipperId"]').selectOption(shipperId);
    await page.locator('select[name="cargoType"]').selectOption('DANGEROUS_GOODS');
    await page.locator('input[name="weightKg"]').fill('200');
    await page.locator('input[name="quantity"]').fill('1');
    await page.locator('input[name="originLocation"]').fill('JPTYO');
    await page.locator('input[name="destinationLocation"]').fill('USNYC');
    await page.locator('input[name="requestedPickupDate"]').fill(futureDateStr(1));
    await page.locator('input[name="requestedDeliveryDate"]').fill(futureDateStr(4));
    // unNumber は意図的に未入力

    await page.locator('form[action="/bookings"] button[type="submit"]').click();

    // バリデーションエラーで登録フォームに留まる
    await expect(page).toHaveURL('/bookings');
    await expect(page.locator('.alert-danger')).toContainText('UN 番号は危険物の場合に必須です');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// E08: 冷凍貨物予約（US05 受入条件）
// ─────────────────────────────────────────────────────────────────────────────
test.describe('E08: 冷凍貨物予約', () => {
  let shipperId: string;

  test.beforeEach(async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      '冷凍テスト荷主',
      `refrig-${Date.now()}@example.com`,
    );
    shipperId = await shipperPage.extractShipperId();
  });

  test('温度帯を入力して冷凍貨物予約を登録できる', async ({ page }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'REFRIGERATED',
      weightKg: '300',
      quantity: '2',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(1),
      requestedDeliveryDate: futureDateStr(3),
      minTempCelsius: '-18',
      maxTempCelsius: '-10',
    });

    await expect(page).toHaveURL(
      /\/bookings\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/,
    );
    await expect(page.locator('.alert-success')).toContainText('予約を登録しました');
  });

  test('温度帯を入力しないと冷凍貨物予約は登録できない', async ({ page }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.goto();

    await page.locator('select[name="shipperId"]').selectOption(shipperId);
    await page.locator('select[name="cargoType"]').selectOption('REFRIGERATED');
    await page.locator('input[name="weightKg"]').fill('300');
    await page.locator('input[name="quantity"]').fill('1');
    await page.locator('input[name="originLocation"]').fill('JPTYO');
    await page.locator('input[name="destinationLocation"]').fill('SGSIN');
    await page.locator('input[name="requestedPickupDate"]').fill(futureDateStr(1));
    await page.locator('input[name="requestedDeliveryDate"]').fill(futureDateStr(3));
    // 温度帯は意図的に未入力

    await page.locator('form[action="/bookings"] button[type="submit"]').click();

    // バリデーションエラーで登録フォームに留まる
    await expect(page).toHaveURL('/bookings');
    await expect(page.locator('.alert-danger')).toContainText('温度範囲は冷凍貨物の場合に必須です');
  });
});
