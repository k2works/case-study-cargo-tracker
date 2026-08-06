import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { VoyagePage } from '../pages/VoyagePage';
import { RouteCandidatePage } from '../pages/RouteCandidatePage';
import { HandlingPage } from '../pages/HandlingPage';

/** US15 荷役作業記録 E2E。Receive → Load の順次登録で TrackingStatus が遷移し荷役一覧に反映。 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

async function issueTracking(page: import('@playwright/test').Page): Promise<{ bookingId: string; trackingNumber: string; voyageNumber: string }> {
  const suffix = uniqueSuffix();
  const voyageNumber = `VY-US15-${suffix}`;
  const voyage = new VoyagePage(page);
  await voyage.gotoNew();
  await voyage.fillRegister({
    voyageNumber,
    departureLocation: 'JPYOK',
    arrivalLocation: 'USNYC',
    departureTime: '2099-07-01T10:00',
    arrivalTime: '2099-07-10T18:00',
  });
  await voyage.submitRegister();

  const shipper = new ShipperPage(page);
  await shipper.gotoNew();
  await shipper.fillForm({
    name: 'US15 テスト荷主',
    email: `us15-${suffix}@example.com`,
    phone: '03-0000-0000',
    address: '東京都',
    shipperType: 'Individual',
  });
  await shipper.submit();
  await shipper.gotoList();
  const shipperId = ((await page.locator('tr', { hasText: `us15-${suffix}@example.com` }).textContent()) ?? '').match(/SH-\S+/)?.[0]!;

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
  await page.locator('button[data-action="issue-tracking"]').click();

  // 詳細画面に表示された追跡番号を取得
  const trackingNumber = ((await page.locator('.alert-primary').textContent()) ?? '').match(/TN-\d+/)?.[0]!;
  return { bookingId, trackingNumber, voyageNumber };
}

test.describe('US15 荷役作業記録', () => {
  test('Receive を登録すると一覧に表示され追跡照会で状態が Received になる', async ({
    page,
    loggedIn,
  }) => {
    const { trackingNumber } = await issueTracking(page);

    const handling = new HandlingPage(page);
    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Receive',
      completionDateTime: '2099-07-01T08:00',
      locationUnLocode: 'JPYOK',
      operatorName: '山田太郎',
    });
    await handling.submit();
    await expect(page).toHaveURL('/handling');
    await expect(page.locator('.alert-success')).toContainText(trackingNumber);
    await expect(page.locator('table')).toContainText('Receive');
    await expect(page.locator('table')).toContainText('JPYOK');

    // 追跡照会で Received 確認
    await page.goto(`/tracking/${trackingNumber}`);
    await expect(page.locator('.badge')).toContainText('Received');
  });

  test('Receive → Load を順次登録すると TrackingStatus が Loaded に遷移する', async ({
    page,
    loggedIn,
  }) => {
    const { trackingNumber, voyageNumber } = await issueTracking(page);

    const handling = new HandlingPage(page);
    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Receive',
      completionDateTime: '2099-07-01T08:00',
      locationUnLocode: 'JPYOK',
    });
    await handling.submit();

    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Load',
      completionDateTime: '2099-07-01T12:00',
      locationUnLocode: 'JPYOK',
      voyageNumber,
    });
    await handling.submit();

    await page.goto(`/tracking/${trackingNumber}`);
    await expect(page.locator('.badge')).toContainText('Loaded');
    await expect(page.locator('body')).toContainText('Receive');
    await expect(page.locator('body')).toContainText('Load');
  });

  test('存在しない追跡番号で登録すると登録フォームに error フラッシュ', async ({ page, loggedIn }) => {
    const handling = new HandlingPage(page);
    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber: 'TN-999999',
      eventType: 'Receive',
      completionDateTime: '2099-07-01T08:00',
      locationUnLocode: 'JPYOK',
    });
    await handling.submit();
    await expect(page).toHaveURL('/handling/new');
    await expect(page.locator('.alert-danger')).toContainText('見つかりません');
  });
});
