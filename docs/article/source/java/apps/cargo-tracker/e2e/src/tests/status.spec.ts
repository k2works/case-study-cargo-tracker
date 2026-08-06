import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingNewPage, BookingShowPage } from '../pages/BookingPage';
import { StatusUpdatePage } from '../pages/TrackingPage';

async function registerShipperAndGetId(page: any, name: string, email: string): Promise<string> {
  const newPage = new ShipperNewPage(page);
  await newPage.goto();
  await newPage.fillIndividual(name, email, '090-0000-0001');
  await newPage.submit();
  await expect(page).toHaveURL(/\/shippers\/.+/);
  const url = page.url() as string;
  const parts = url.split('/');
  return parts[parts.length - 1];
}

async function setupTrackingIssuedBooking(page: any): Promise<string> {
  const timestamp = Date.now();
  const shipperId = await registerShipperAndGetId(
    page,
    `手動更新テスト荷主 ${timestamp}`,
    `status-update-${timestamp}@example.com`
  );

  const bookingNewPage = new BookingNewPage(page);
  const bookingShowPage = new BookingShowPage(page);

  await bookingNewPage.goto();
  await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USLAX', '2027-12-31');
  await bookingNewPage.submit();

  await bookingShowPage.clickConfirm();
  await expect(bookingShowPage.getDetailValue('状態')).toHaveText('予約確定');

  await bookingShowPage.clickIssueTracking();
  await expect(bookingShowPage.getDetailValue('状態')).toHaveText('追跡番号発行済');

  const trackingNumberElement = bookingShowPage.getDetailValue('追跡番号');
  return await trackingNumberElement.innerText();
}

test.describe('US17: 貨物状態を手動更新する', () => {
  test('受入条件1: 手動状態更新フォームが表示される', async ({ page, loggedIn }) => {
    const statusPage = new StatusUpdatePage(page);
    await statusPage.goto();

    await expect(page).toHaveURL('/tracking/status');
    await expect(statusPage.heading).toBeVisible();
    await expect(statusPage.trackingNumberInput).toBeVisible();
    await expect(statusPage.newStatusSelect).toBeVisible();
  });

  test('受入条件2,3: 追跡番号を指定して貨物状態を手動更新できる', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);
    const statusPage = new StatusUpdatePage(page);

    await statusPage.goto();
    await statusPage.fill(trackingNumber.trim(), 'LOADED', 'JPTYO', '2027-01-02T10:00');
    await statusPage.submit();

    await expect(statusPage.successAlert).toBeVisible();
    await expect(statusPage.successAlert).toContainText('状態を更新しました');
  });

  test('受入条件4（異常系）: 存在しない追跡番号の場合エラーメッセージが表示される', async ({ page, loggedIn }) => {
    const statusPage = new StatusUpdatePage(page);

    await statusPage.goto();
    await statusPage.fill('TRK-00000000-NOTEXIST', 'LOADED', 'JPTYO', '2027-01-02T10:00');
    await statusPage.submit();

    await expect(statusPage.errorAlert).toBeVisible();
  });
});
