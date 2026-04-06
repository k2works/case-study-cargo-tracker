import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingIndexPage, BookingNewPage, BookingShowPage } from '../pages/BookingPage';

async function registerShipperAndGetId(page: any, name: string, email: string): Promise<string> {
  const newPage = new ShipperNewPage(page);

  await newPage.goto();
  await newPage.fillIndividual(name, email, '090-0000-0001');
  await newPage.submit();

  // 登録後は /shippers/{id} へリダイレクト
  await expect(page).toHaveURL(/\/shippers\/.+/);
  const url = page.url() as string;
  const parts = url.split('/');
  return parts[parts.length - 1];
}

test.describe('貨物予約管理', () => {
  test('予約一覧ページが表示される', async ({ page, loggedIn }) => {
    const indexPage = new BookingIndexPage(page);
    await indexPage.goto();

    await expect(page).toHaveURL('/bookings');
    await expect(indexPage.heading).toHaveText('予約管理');
    await expect(indexPage.registerButton).toBeVisible();
    await expect(indexPage.table).toBeVisible();
  });

  test('貨物予約を登録できる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperName = `予約テスト荷主 ${timestamp}`;
    const shipperEmail = `booking-shipper-${timestamp}@example.com`;

    const shipperId = await registerShipperAndGetId(page, shipperName, shipperEmail);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();

    await expect(page).toHaveURL('/bookings/new');
    await expect(bookingNewPage.heading).toHaveText('予約登録');

    await bookingNewPage.fill(
      shipperId,
      'GENERAL',
      '100.5',
      'JPTYO',
      'USLAX',
      '2027-12-31'
    );
    await bookingNewPage.submit();

    // 登録後は詳細ページへリダイレクト
    await expect(page).toHaveURL(/\/bookings\/.+/);
    await expect(bookingShowPage.heading).toHaveText('予約詳細');
    await expect(bookingShowPage.getDetailValue('出発地')).toHaveText('JPTYO');
    await expect(bookingShowPage.getDetailValue('目的地')).toHaveText('USLAX');
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('仮受付');
  });

  test('貨物予約詳細ページが表示される', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperName = `詳細テスト荷主 ${timestamp}`;
    const shipperEmail = `detail-booking-${timestamp}@example.com`;

    const shipperId = await registerShipperAndGetId(page, shipperName, shipperEmail);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();

    await bookingNewPage.fillRefrigerated(
      shipperId,
      '50.0',
      'JPOSA',
      'GBLON',
      '2027-06-30',
      '-25',
      '-18',
      'CELSIUS'
    );
    await bookingNewPage.submit();

    // 詳細ページの内容を確認
    await expect(page).toHaveURL(/\/bookings\/.+/);
    await expect(bookingShowPage.heading).toHaveText('予約詳細');
    await expect(bookingShowPage.getDetailValue('出発地')).toHaveText('JPOSA');
    await expect(bookingShowPage.getDetailValue('目的地')).toHaveText('GBLON');
    await expect(bookingShowPage.getDetailValue('貨物種別')).toHaveText('冷凍・冷蔵');
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('仮受付');
    await expect(bookingShowPage.backButton).toBeVisible();

    // 一覧へ戻る
    await bookingShowPage.backButton.click();
    await expect(page).toHaveURL('/bookings');
  });

  test('危険物貨物の予約を登録できる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperName = `危険物テスト荷主 ${timestamp}`;
    const shipperEmail = `hazardous-booking-${timestamp}@example.com`;

    const shipperId = await registerShipperAndGetId(page, shipperName, shipperEmail);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();

    await bookingNewPage.fillHazardous(
      shipperId,
      '50.0',
      'JPTYO',
      'USLAX',
      '2027-12-31',
      '3',
      'UN1203',
      'ガソリン'
    );
    await bookingNewPage.submit();

    // 詳細ページにリダイレクトされ、貨物種別が HAZARDOUS であることを確認
    await expect(page).toHaveURL(/\/bookings\/.+/);
    await expect(bookingShowPage.heading).toHaveText('予約詳細');
    await expect(bookingShowPage.getDetailValue('貨物種別')).toHaveText('危険物');
    await expect(bookingShowPage.getDetailValue('出発地')).toHaveText('JPTYO');
    await expect(bookingShowPage.getDetailValue('目的地')).toHaveText('USLAX');

    // 危険物情報が表示されることを確認
    await expect(bookingShowPage.getDetailValue('危険物クラス')).toHaveText('3');
    await expect(bookingShowPage.getDetailValue('UN番号')).toHaveText('UN1203');
  });

  test('冷凍・冷蔵貨物の予約を登録できる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperName = `冷凍テスト荷主 ${timestamp}`;
    const shipperEmail = `refrigerated-booking-${timestamp}@example.com`;

    const shipperId = await registerShipperAndGetId(page, shipperName, shipperEmail);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();

    await bookingNewPage.fillRefrigerated(
      shipperId,
      '30.0',
      'JPOSA',
      'SGSIN',
      '2027-06-30',
      '-25',
      '-18',
      'CELSIUS'
    );
    await bookingNewPage.submit();

    // 詳細ページにリダイレクトされ、貨物種別が REFRIGERATED であることを確認
    await expect(page).toHaveURL(/\/bookings\/.+/);
    await expect(bookingShowPage.heading).toHaveText('予約詳細');
    await expect(bookingShowPage.getDetailValue('貨物種別')).toHaveText('冷凍・冷蔵');
    await expect(bookingShowPage.getDetailValue('出発地')).toHaveText('JPOSA');
    await expect(bookingShowPage.getDetailValue('目的地')).toHaveText('SGSIN');

    // 温度管理情報が表示されることを確認
    await expect(bookingShowPage.getDetailValue('温度管理')).toContainText('-25');
    await expect(bookingShowPage.getDetailValue('温度管理')).toContainText('-18');
  });

  test('予約を確定できる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `確定テスト荷主 ${timestamp}`, `confirm-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USLAX', '2027-12-31');
    await bookingNewPage.submit();

    // 仮受付状態であることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('仮受付');
    await expect(bookingShowPage.confirmButton).toBeVisible();

    // 予約確定操作
    await bookingShowPage.clickConfirm();

    // 状態が「予約確定」になっていることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('予約確定');
    await expect(bookingShowPage.successAlert).toBeVisible();
    // 確定後は確定ボタンが非表示になる
    await expect(bookingShowPage.confirmButton).not.toBeVisible();
  });

  test('予約をキャンセルできる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `キャンセルテスト荷主 ${timestamp}`, `cancel-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USLAX', '2027-12-31');
    await bookingNewPage.submit();

    // 仮受付状態であることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('仮受付');
    await expect(bookingShowPage.cancelButton).toBeVisible();

    // キャンセル操作
    await bookingShowPage.clickCancel();

    // 状態が「キャンセル」になっていることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('キャンセル');
    await expect(bookingShowPage.successAlert).toBeVisible();
    // キャンセル後はキャンセルボタンが非表示になる
    await expect(bookingShowPage.cancelButton).not.toBeVisible();
  });

  test('予約登録フォームから一覧へ戻れる', async ({ page, loggedIn }) => {
    const bookingNewPage = new BookingNewPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.backButton.click();

    await expect(page).toHaveURL('/bookings');
  });
});
