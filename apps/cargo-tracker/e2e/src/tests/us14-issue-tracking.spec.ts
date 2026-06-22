import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { VoyagePage } from '../pages/VoyagePage';
import { RouteCandidatePage } from '../pages/RouteCandidatePage';

/** US14 追跡番号発行 E2E。Confirmed → TrackingIssued 遷移 + 追跡 URL 表示 + 冪等性。 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

async function bootedConfirmed(page: import('@playwright/test').Page): Promise<string> {
  const suffix = uniqueSuffix();
  const voyage = new VoyagePage(page);
  await voyage.gotoNew();
  await voyage.fillRegister({
    voyageNumber: `VY-US14-${suffix}`,
    departureLocation: 'JPYOK',
    arrivalLocation: 'USNYC',
    departureTime: '2099-07-01T10:00',
    arrivalTime: '2099-07-10T18:00',
  });
  await voyage.submitRegister();

  const shipper = new ShipperPage(page);
  await shipper.gotoNew();
  await shipper.fillForm({
    name: 'US14 テスト荷主',
    email: `us14-${suffix}@example.com`,
    phone: '03-0000-0000',
    address: '東京都',
    shipperType: 'Individual',
  });
  await shipper.submit();
  await shipper.gotoList();
  const shipperId = ((await page.locator('tr', { hasText: `us14-${suffix}@example.com` }).textContent()) ?? '').match(/SH-\S+/)?.[0]!;

  const booking = new BookingPage(page);
  await booking.gotoNew();
  await booking.fillForm({
    shipperCode: shipperId,
    origin: 'JPYOK',
    destination: 'USNYC',
    arrivalDeadline: "2099-12-31",
    cargoType: 'General',
    weightKg: 500,
  });
  await booking.submit();
  const bookingId = page.url().split('/').pop()!;
  await booking.assignToRouting();

  const routes = new RouteCandidatePage(page);
  await routes.gotoCandidates(bookingId);
  await routes.confirmCandidate(0);

  await booking.gotoDetail(bookingId);
  await page.locator('button[data-action="confirm"]').click();
  await expect(page.locator('.badge')).toContainText('Confirmed');
  return bookingId;
}

test.describe('US14 追跡番号発行', () => {
  test('Confirmed 予約から追跡番号を発行すると TrackingIssued に遷移し追跡 URL バッジが表示される', async ({
    page,
    loggedIn,
  }) => {
    const bookingId = await bootedConfirmed(page);
    const booking = new BookingPage(page);
    await booking.gotoDetail(bookingId);

    await page.locator('button[data-action="issue-tracking"]').click();
    await expect(page).toHaveURL(`/bookings/${bookingId}`);
    await expect(page.locator('.alert-success')).toContainText('追跡番号 TN-');
    await expect(page.locator('.badge')).toContainText('TrackingIssued');

    // 詳細画面に追跡番号バッジ + 公開追跡 URL リンク
    await expect(page.locator('.alert-primary')).toContainText('追跡番号');
    await expect(page.locator('a[href^="/public/tracking/TN-"]')).toBeVisible();
  });

  test('発行済の予約は「追跡番号を発行」ボタンが非表示（冪等性 UI 反映）', async ({
    page,
    loggedIn,
  }) => {
    const bookingId = await bootedConfirmed(page);
    const booking = new BookingPage(page);
    await booking.gotoDetail(bookingId);
    await page.locator('button[data-action="issue-tracking"]').click();
    await booking.gotoDetail(bookingId);

    await expect(page.locator('button[data-action="issue-tracking"]')).toHaveCount(0);
  });
});
