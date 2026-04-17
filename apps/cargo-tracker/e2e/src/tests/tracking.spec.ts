import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingIndexPage, BookingNewPage, BookingShowPage } from '../pages/BookingPage';

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

async function setupConfirmedBooking(page: any): Promise<BookingShowPage> {
  const timestamp = Date.now();
  const shipperId = await registerShipperAndGetId(
    page,
    `追跡テスト荷主 ${timestamp}`,
    `tracking-${timestamp}@example.com`
  );

  const bookingNewPage = new BookingNewPage(page);
  const bookingShowPage = new BookingShowPage(page);

  await bookingNewPage.goto();
  await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USLAX', '2027-12-31');
  await bookingNewPage.submit();

  // PRELIMINARY → CONFIRMED
  await bookingShowPage.clickConfirm();
  await expect(bookingShowPage.getDetailValue('状態')).toHaveText('予約確定');

  return bookingShowPage;
}

test.describe('US14: 追跡番号を発行する', () => {
  test('受入条件1: 予約確定済みの予約に追跡番号を発行できる', async ({ page, loggedIn }) => {
    const bookingShowPage = await setupConfirmedBooking(page);

    // CONFIRMED 状態で「追跡番号を発行」ボタンが表示される
    await expect(bookingShowPage.issueTrackingButton).toBeVisible();

    // 追跡番号発行操作
    await bookingShowPage.clickIssueTracking();

    // 成功メッセージが表示される
    await expect(bookingShowPage.successAlert).toBeVisible();
    await expect(bookingShowPage.successAlert).toContainText('追跡番号を発行しました');
  });

  test('受入条件2: 追跡番号発行後、状態が「追跡番号発行済」に遷移する', async ({ page, loggedIn }) => {
    const bookingShowPage = await setupConfirmedBooking(page);

    await bookingShowPage.clickIssueTracking();

    // 状態が「追跡番号発行済」になっていることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('追跡番号発行済');
    // 発行後は「追跡番号を発行」ボタンが非表示になる
    await expect(bookingShowPage.issueTrackingButton).not.toBeVisible();
  });

  test('受入条件3: 発行された追跡番号が詳細画面に表示される', async ({ page, loggedIn }) => {
    const bookingShowPage = await setupConfirmedBooking(page);

    await bookingShowPage.clickIssueTracking();

    // 追跡番号が表示されていることを確認（TRK-YYYYMMDD-XXXXXXXX 形式）
    const trackingNumber = bookingShowPage.getDetailValue('追跡番号');
    await expect(trackingNumber).toBeVisible();
    await expect(trackingNumber).toContainText('TRK-');
  });

  test('受入条件4（異常系）: 仮受付状態の予約には追跡番号発行ボタンが表示されない', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(
      page,
      `追跡異常テスト荷主 ${timestamp}`,
      `tracking-error-${timestamp}@example.com`
    );

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USLAX', '2027-12-31');
    await bookingNewPage.submit();

    // PRELIMINARY 状態では追跡番号発行ボタンが表示されない
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('仮受付');
    await expect(bookingShowPage.issueTrackingButton).not.toBeVisible();
  });
});
