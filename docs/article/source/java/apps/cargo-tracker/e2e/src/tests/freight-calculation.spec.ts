import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingNewPage, BookingShowPage, BookingRoutePage } from '../pages/BookingPage';
import { FreightCalculationPage } from '../pages/BillingPage';

// 経路確定済みの予約をセットアップする（CargoRoutedEvent → 精算書自動生成）
async function setupRoutedBooking(page: any): Promise<{ bookingId: string }> {
  const timestamp = Date.now();
  const shipperName = `料金算出テスト荷主 ${timestamp}`;
  const shipperEmail = `freight-test-${timestamp}@example.com`;

  const shipperNewPage = new ShipperNewPage(page);
  await shipperNewPage.goto();
  await shipperNewPage.fillCorporate(shipperName, shipperEmail, '090-0000-8888', 'CT-FC01', '0.00');
  await shipperNewPage.submit();
  await expect(page).toHaveURL(/\/shippers\/.+/);
  const shipperUrl = page.url() as string;
  const shipperId = shipperUrl.split('/').pop()!;

  const bookingNewPage = new BookingNewPage(page);
  const bookingShowPage = new BookingShowPage(page);
  const bookingRoutePage = new BookingRoutePage(page);

  await bookingNewPage.goto();
  await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
  await bookingNewPage.submit();

  const bookingUrl = page.url() as string;
  const bookingId = bookingUrl.split('/').pop()!;

  // PRELIMINARY → ROUTE_PROPOSED
  await bookingShowPage.clickAssignToRouting();
  await expect(bookingShowPage.getDetailValue('状態')).toHaveText('経路提案済');

  // 経路割り当て（CargoRoutedEvent 発火 → 精算書自動生成）
  await bookingShowPage.clickRouteAssign();
  await bookingRoutePage.selectVoyage('V001');
  await bookingRoutePage.submitRoute();

  await expect(page).toHaveURL(/\/bookings\/[^/]+$/);

  return { bookingId };
}

test.describe('US21: 輸送料金を算出する', () => {
  test('受入条件1: 輸送料金算出画面に遷移できる', async ({ page, loggedIn }) => {
    const freightPage = new FreightCalculationPage(page);

    await freightPage.goto();

    await expect(page).toHaveURL('/billing/freight-calculation');
    await expect(freightPage.heading).toHaveText('輸送料金算出');
    await expect(freightPage.bookingIdInput).toBeVisible();
    await expect(freightPage.searchButton).toBeVisible();
  });

  test('受入条件2: 経路確定済みの予約IDを入力すると基本料金が表示される', async ({ page, loggedIn }) => {
    const { bookingId } = await setupRoutedBooking(page);
    const freightPage = new FreightCalculationPage(page);

    await freightPage.goto();
    await freightPage.searchByBookingId(bookingId);

    await expect(page).toHaveURL(/\/billing\/freight-calculation\?bookingId=.+/);
    // 基本料金が表示される
    await expect(page.locator('text=基本料金（税抜）')).toBeVisible();
    await expect(page.locator('dt', { hasText: '基本料金（税抜）' }).locator('~ dd').first()).toContainText('円');
  });

  test('受入条件3: 調整なしで料金を確定できる', async ({ page, loggedIn }) => {
    const { bookingId } = await setupRoutedBooking(page);
    const freightPage = new FreightCalculationPage(page);

    await freightPage.goto();
    await freightPage.searchByBookingId(bookingId);
    await freightPage.confirmFreight(0, '');

    // 確定後は請求一覧にリダイレクトされる
    await expect(page).toHaveURL('/billing/invoices');
    await expect(freightPage.successAlert).toBeVisible();
    await expect(freightPage.successAlert).toContainText('輸送料金を確定しました');
  });

  test('受入条件4: 調整額を入力して料金を確定できる', async ({ page, loggedIn }) => {
    const { bookingId } = await setupRoutedBooking(page);
    const freightPage = new FreightCalculationPage(page);

    await freightPage.goto();
    await freightPage.searchByBookingId(bookingId);
    await freightPage.confirmFreight(-10000, '遅延補償');

    await expect(page).toHaveURL('/billing/invoices');
    await expect(freightPage.successAlert).toBeVisible();
  });

  test('受入条件5（異常系）: 存在しない予約IDはエラーを表示する', async ({ page, loggedIn }) => {
    const freightPage = new FreightCalculationPage(page);

    await freightPage.goto();
    await freightPage.searchByBookingId('BK-NOT-EXIST');

    await expect(page).toHaveURL(/\/billing\/freight-calculation\?bookingId=.+/);
    await expect(page.locator('text=請求書が見つかりませんでした')).toBeVisible();
  });
});
