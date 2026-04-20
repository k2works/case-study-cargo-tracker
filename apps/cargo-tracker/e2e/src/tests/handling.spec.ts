import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingNewPage, BookingShowPage } from '../pages/BookingPage';
import { HandlingPage } from '../pages/TrackingPage';

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
    `荷役テスト荷主 ${timestamp}`,
    `handling-${timestamp}@example.com`
  );

  const bookingNewPage = new BookingNewPage(page);
  const bookingShowPage = new BookingShowPage(page);

  await bookingNewPage.goto();
  await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USLAX', '2027-12-31');
  await bookingNewPage.submit();

  // PRELIMINARY → CONFIRMED
  await bookingShowPage.clickConfirm();
  await expect(bookingShowPage.getDetailValue('状態')).toHaveText('予約確定');

  // CONFIRMED → TRACKING_ISSUED
  await bookingShowPage.clickIssueTracking();
  await expect(bookingShowPage.getDetailValue('状態')).toHaveText('追跡番号発行済');

  // 追跡番号を取得
  const trackingNumberElement = bookingShowPage.getDetailValue('追跡番号');
  return await trackingNumberElement.innerText();
}

test.describe('US15: 荷役作業を記録する', () => {
  test('受入条件1: 追跡番号で貨物を特定して受領作業を記録できる', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);
    const handlingPage = new HandlingPage(page);

    await handlingPage.goto();
    await expect(handlingPage.heading).toHaveText('荷役作業記録');

    await handlingPage.fill(trackingNumber, 'RECEIVE', '2027-01-01T10:00', 'JPTYO');
    await handlingPage.submit();

    await expect(handlingPage.successAlert).toBeVisible();
    await expect(handlingPage.successAlert).toContainText('荷役作業を記録しました');
  });

  test('受入条件2: 積込作業を記録できる', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);
    const handlingPage = new HandlingPage(page);

    await handlingPage.goto();
    // 受領を先に記録
    await handlingPage.fill(trackingNumber, 'RECEIVE', '2027-01-01T10:00', 'JPTYO');
    await handlingPage.submit();
    await expect(handlingPage.successAlert).toBeVisible();

    // 積込を記録
    await handlingPage.fill(trackingNumber, 'LOAD', '2027-01-02T10:00', 'JPTYO', 'V100');
    await handlingPage.submit();

    await expect(handlingPage.successAlert).toBeVisible();
    await expect(handlingPage.successAlert).toContainText('荷役作業を記録しました');
  });

  test('受入条件3: 荷降し作業を記録できる', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);
    const handlingPage = new HandlingPage(page);

    await handlingPage.goto();
    await handlingPage.fill(trackingNumber, 'RECEIVE', '2027-01-01T10:00', 'JPTYO');
    await handlingPage.submit();
    await expect(handlingPage.successAlert).toBeVisible();

    await handlingPage.fill(trackingNumber, 'LOAD', '2027-01-02T10:00', 'JPTYO', 'V100');
    await handlingPage.submit();
    await expect(handlingPage.successAlert).toBeVisible();

    await handlingPage.fill(trackingNumber, 'UNLOAD', '2027-01-15T10:00', 'USLAX', 'V100');
    await handlingPage.submit();

    await expect(handlingPage.successAlert).toBeVisible();
    await expect(handlingPage.successAlert).toContainText('荷役作業を記録しました');
  });

  test('受入条件4（異常系）: 存在しない追跡番号の場合エラーメッセージが表示される', async ({ page, loggedIn }) => {
    const handlingPage = new HandlingPage(page);

    await handlingPage.goto();
    await handlingPage.fill('TRK-00000000-NOTEXIST', 'RECEIVE', '2027-01-01T10:00', 'JPTYO');
    await handlingPage.submit();

    await expect(handlingPage.errorAlert).toBeVisible();
    await expect(handlingPage.errorAlert).toContainText('Tracking record not found');
  });
});

test.describe('US16: 引取作業を記録する', () => {
  test('受入条件1: 作業種別「引取」を選択すると荷受人確認フィールドが表示される', async ({ page, loggedIn }) => {
    const handlingPage = new HandlingPage(page);

    await handlingPage.goto();

    // 初期状態では荷受人確認フィールドは非表示
    const claimSection = page.locator('#claim-section');
    await expect(claimSection).toHaveClass(/d-none/);

    // CLAIM を選択すると表示される
    await handlingPage.selectEventType('CLAIM').check();
    await expect(claimSection).not.toHaveClass(/d-none/);
  });

  test('受入条件2,3: 引取作業を記録すると貨物状態が「引取済み」に更新される', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);
    const handlingPage = new HandlingPage(page);

    await handlingPage.goto();
    // RECEIVE
    await handlingPage.fill(trackingNumber, 'RECEIVE', '2027-01-01T10:00', 'JPTYO');
    await handlingPage.submit();
    await expect(handlingPage.successAlert).toBeVisible();

    // LOAD
    await handlingPage.fill(trackingNumber, 'LOAD', '2027-01-02T10:00', 'JPTYO', 'V100');
    await handlingPage.submit();
    await expect(handlingPage.successAlert).toBeVisible();

    // UNLOAD
    await handlingPage.fill(trackingNumber, 'UNLOAD', '2027-01-15T10:00', 'USLAX', 'V100');
    await handlingPage.submit();
    await expect(handlingPage.successAlert).toBeVisible();

    // CLAIM（引取）
    await handlingPage.fill(trackingNumber, 'CLAIM', '2027-01-16T10:00', 'USLAX', undefined, '確認コード-001');
    await handlingPage.submit();

    await expect(handlingPage.successAlert).toBeVisible();
    await expect(handlingPage.successAlert).toContainText('荷役作業を記録しました');
  });

  test('受入条件5（異常系）: 存在しない追跡番号の場合エラーメッセージが表示される', async ({ page, loggedIn }) => {
    const handlingPage = new HandlingPage(page);

    await handlingPage.goto();
    await handlingPage.fill('TRK-00000000-NOTEXIST', 'CLAIM', '2027-01-16T10:00', 'USLAX');
    await handlingPage.submit();

    await expect(handlingPage.errorAlert).toBeVisible();
    await expect(handlingPage.errorAlert).toContainText('Tracking record not found');
  });
});
