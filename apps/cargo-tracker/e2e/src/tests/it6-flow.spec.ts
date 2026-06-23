import { Page } from '@playwright/test';
import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { VoyagePage } from '../pages/VoyagePage';
import { RouteCandidatePage } from '../pages/RouteCandidatePage';
import { HandlingPage } from '../pages/HandlingPage';

/** IT6 統合 E2E: US16 引取作業 / US17 状態手動更新 / US21 輸送料金算出。 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

async function setupClaimedBooking(page: Page): Promise<{ bookingId: string; trackingNumber: string; voyageNumber: string; suffix: string }> {
  const suffix = uniqueSuffix();
  const voyageNumber = `VY-IT6-${suffix}`;

  const voyage = new VoyagePage(page);
  await voyage.gotoNew();
  await voyage.fillRegister({
    voyageNumber,
    departureLocation: 'JPYOK',
    arrivalLocation: 'USNYC',
    departureTime: '2099-09-01T10:00',
    arrivalTime: '2099-09-10T18:00',
  });
  await voyage.submitRegister();

  const shipper = new ShipperPage(page);
  await shipper.gotoNew();
  await shipper.fillForm({
    name: 'IT6 テスト荷主',
    email: `it6-${suffix}@example.com`,
    phone: '03-0000-0000',
    address: '東京都',
    shipperType: 'Individual',
  });
  await shipper.submit();
  await shipper.gotoList();
  const shipperId = ((await page.locator('tr', { hasText: `it6-${suffix}@example.com` }).textContent()) ?? '').match(/SH-\S+/)?.[0]!;

  const booking = new BookingPage(page);
  await booking.gotoNew();
  await booking.fillForm({
    shipperCode: shipperId,
    origin: 'JPYOK',
    destination: 'USNYC',
    arrivalDeadline: '2099-12-31',
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
  const trackingNumber = ((await page.locator('.alert-primary').textContent()) ?? '').match(/TN-\d+/)?.[0]!;

  return { bookingId, trackingNumber, voyageNumber, suffix };
}

test.describe('IT6 US16 引取作業', () => {

  test('Claim を荷受人確認付きで登録すると配送完了 + 貨物状態 Delivered', async ({ page, loggedIn }) => {
    const { bookingId, trackingNumber } = await setupClaimedBooking(page);

    const handling = new HandlingPage(page);
    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Claim',
      completionDateTime: '2099-09-11T10:00',
      locationUnLocode: 'USNYC',
      operatorName: '田中',
      recipientConfirmation: '署名: 山田太郎',
    });
    await handling.submit();
    await expect(page).toHaveURL('/handling');
    await expect(page.locator('.alert-success')).toContainText(trackingNumber);

    // 追跡照会で Claimed 確認
    await page.goto(`/tracking/${trackingNumber}`);
    await expect(page.locator('.badge')).toContainText('Claimed');

    // 予約詳細で Delivered 確認
    await page.goto(`/bookings/${bookingId}`);
    await expect(page.locator('body')).toContainText('Delivered');
  });

  test('Claim を荷受人確認なしで登録するとエラー', async ({ page, loggedIn }) => {
    const { trackingNumber } = await setupClaimedBooking(page);

    const handling = new HandlingPage(page);
    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Claim',
      completionDateTime: '2099-09-11T10:00',
      locationUnLocode: 'USNYC',
    });
    await handling.submit();
    await expect(page).toHaveURL('/handling/new');
    await expect(page.locator('.alert-danger')).toContainText('荷受人確認');
  });
});

test.describe('IT6 US17 状態手動更新', () => {

  test('Tracker が追跡詳細から状態を Loaded に手動更新できる', async ({ page, loggedIn }) => {
    const { trackingNumber } = await setupClaimedBooking(page);

    await page.goto(`/tracking/${trackingNumber}`);
    await page.locator('button[data-bs-target="#manualStatusModal"]').click();
    await page.locator('select[name="status"]').selectOption('Loaded');
    await page.locator('input[name="locationUnLocode"]').fill('JPYOK');
    await page.locator('input[name="occurredAt"]').fill('2099-09-02T10:00');
    await page.locator('#manualStatusModal button[type="submit"]').click();

    await expect(page.locator('.alert-success')).toContainText('Loaded');
    await expect(page.locator('.badge').first()).toContainText('Loaded');
  });
});

test.describe('IT6 US21 輸送料金算出', () => {

  test('引取済予約から請求書を発行し一覧 + 詳細に表示される', async ({ page, loggedIn }) => {
    const { bookingId, trackingNumber } = await setupClaimedBooking(page);

    // Claim 登録で Delivered 化
    const handling = new HandlingPage(page);
    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Claim',
      completionDateTime: '2099-09-11T10:00',
      locationUnLocode: 'USNYC',
      recipientConfirmation: '署名',
    });
    await handling.submit();

    // 請求書発行
    await page.goto('/billing/invoices/new');
    await page.locator('input[name="bookingId"]').fill(bookingId);
    await page.locator('button[type="submit"]:has-text("発行")').click();

    // 詳細にリダイレクトされ Pending 表示
    await expect(page.locator('h1')).toContainText('請求書 INV-');
    await expect(page.locator('.badge')).toContainText('Pending');
    await expect(page.locator('body')).toContainText(bookingId);

    // 一覧画面で確認
    await page.goto('/billing/invoices');
    await expect(page.locator('table')).toContainText(bookingId);
    await expect(page.locator('table')).toContainText('Pending');
  });

  test('Delivered でない予約は請求書発行不可', async ({ page, loggedIn }) => {
    const { bookingId } = await setupClaimedBooking(page);

    // 引取せず請求しようとする (現状 TrackingIssued 状態)
    await page.goto('/billing/invoices/new');
    await page.locator('input[name="bookingId"]').fill(bookingId);
    await page.locator('button[type="submit"]:has-text("発行")').click();

    await expect(page).toHaveURL('/billing/invoices/new');
    await expect(page.locator('.alert-danger')).toContainText('Delivered');
  });
});
