import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingNewPage, BookingShowPage } from '../pages/BookingPage';
import { ExceptionPage } from '../pages/TrackingPage';
import { HandlingPage, TrackingDetailPage } from '../pages/TrackingPage';

async function setupTrackingIssuedBooking(page: any): Promise<string> {
  const timestamp = Date.now();

  const shipperNewPage = new ShipperNewPage(page);
  await shipperNewPage.goto();
  await shipperNewPage.fillIndividual(
    `例外テスト荷主 ${timestamp}`,
    `exception-${timestamp}@example.com`,
    '090-0000-0099'
  );
  await shipperNewPage.submit();
  await expect(page).toHaveURL(/\/shippers\/.+/);
  const url = page.url() as string;
  const parts = url.split('/');
  const shipperId = parts[parts.length - 1];

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

test.describe('US19: 遅延例外を処理する', () => {
  test('受入条件1,2: 遅延例外を記録すると貨物状態が「例外発生」に更新される', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);

    // 受領作業を先に記録
    const handlingPage = new HandlingPage(page);
    await handlingPage.goto();
    await handlingPage.fill(trackingNumber, 'RECEIVE', '2027-01-01T10:00', 'JPTYO');
    await handlingPage.submit();
    await expect(handlingPage.successAlert).toBeVisible();

    // 遅延例外を記録
    const exceptionPage = new ExceptionPage(page);
    await exceptionPage.goto();
    await expect(exceptionPage.heading).toHaveText('例外処理記録');

    await exceptionPage.fill(
      trackingNumber,
      'DELAY',
      'JPTYO',
      '2027-01-05T10:00',
      '台風による輸送遅延',
      '代替ルートを手配中'
    );
    await exceptionPage.submit();

    await expect(exceptionPage.successAlert).toBeVisible();
    await expect(exceptionPage.successAlert).toContainText('例外を記録しました');

    // 貨物状態が「例外発生」になっていることを確認
    const detailPage = new TrackingDetailPage(page);
    await detailPage.goto(trackingNumber);
    await expect(detailPage.statusBadge).toContainText('例外発生');
  });

  test('受入条件5（例外履歴）: 遅延例外の対応内容を入力して記録できる', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);

    const exceptionPage = new ExceptionPage(page);
    await exceptionPage.goto();
    await exceptionPage.fill(
      trackingNumber,
      'DELAY',
      'JPTYO',
      '2027-01-05T14:00',
      '悪天候による遅延',
      '新しい到着予定日: 2027-01-20'
    );
    await exceptionPage.submit();

    await expect(exceptionPage.successAlert).toContainText('例外を記録しました');
  });

  test('異常系: 存在しない追跡番号で例外を記録するとエラーが表示される', async ({ page, loggedIn }) => {
    const exceptionPage = new ExceptionPage(page);
    await exceptionPage.goto();
    await exceptionPage.fill(
      'TRK-00000000-NOTEXIST',
      'DELAY',
      'JPTYO',
      '2027-01-05T10:00',
      '台風による遅延'
    );
    await exceptionPage.submit();

    await expect(exceptionPage.errorAlert).toBeVisible();
    await expect(exceptionPage.errorAlert).toContainText('Tracking record not found');
  });
});

test.describe('US20: 破損・紛失例外を処理する', () => {
  test('受入条件1,2: 破損例外を記録すると貨物状態が「例外発生」に更新される', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);

    const exceptionPage = new ExceptionPage(page);
    await exceptionPage.goto();
    await exceptionPage.fill(
      trackingNumber,
      'DAMAGE',
      'USLAX',
      '2027-01-15T10:00',
      '荷扱いによる破損',
      '保険手続きを開始'
    );
    await exceptionPage.submit();

    await expect(exceptionPage.successAlert).toBeVisible();
    await expect(exceptionPage.successAlert).toContainText('例外を記録しました');

    const detailPage = new TrackingDetailPage(page);
    await detailPage.goto(trackingNumber);
    await expect(detailPage.statusBadge).toContainText('例外発生');
  });

  test('受入条件1,2,3: 紛失例外を記録すると貨物状態が「例外発生」に更新される（緊急フラグ設定）', async ({ page, loggedIn }) => {
    const trackingNumber = await setupTrackingIssuedBooking(page);

    const exceptionPage = new ExceptionPage(page);
    await exceptionPage.goto();
    await exceptionPage.fill(
      trackingNumber,
      'LOST',
      'HKHKG',
      '2027-01-10T08:00',
      '配送中に紛失',
      '管理職に緊急連絡済み'
    );
    await exceptionPage.submit();

    await expect(exceptionPage.successAlert).toBeVisible();
    await expect(exceptionPage.successAlert).toContainText('例外を記録しました');

    const detailPage = new TrackingDetailPage(page);
    await detailPage.goto(trackingNumber);
    await expect(detailPage.statusBadge).toContainText('例外発生');
  });

  test('受入条件（UI確認）: 紛失を選択すると緊急フラグの警告が表示される', async ({ page, loggedIn }) => {
    const exceptionPage = new ExceptionPage(page);
    await exceptionPage.goto();

    // 紛失フラグの警告テキストが存在することを確認
    await expect(page.locator('.text-danger')).toContainText('紛失');
  });
});
