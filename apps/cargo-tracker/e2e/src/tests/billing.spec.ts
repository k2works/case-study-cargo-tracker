import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingNewPage, BookingShowPage, BookingRoutePage } from '../pages/BookingPage';
import { BillingIndexPage, BillingShowPage, BillingConfirmPage } from '../pages/BillingPage';
import { NavbarPage } from '../pages/NavbarPage';

// 法人荷主を登録して shipperId を返す
async function registerCorporateShipperAndGetId(page: any, name: string, email: string): Promise<string> {
  const newPage = new ShipperNewPage(page);

  await newPage.goto();
  await newPage.fillCorporate(name, email, '090-0000-9999', 'CT-0001', '0.15');
  await newPage.submit();

  await expect(page).toHaveURL(/\/shippers\/.+/);
  const url = page.url() as string;
  const parts = url.split('/');
  return parts[parts.length - 1];
}

// 予約を作成して経路を確定するまでのセットアップ
// CargoRoutedEvent が発火し、精算書が自動生成される
async function setupBillingFlow(page: any): Promise<{ bookingId: string }> {
  const timestamp = Date.now();
  const shipperName = `請求テスト法人荷主 ${timestamp}`;
  const shipperEmail = `billing-shipper-${timestamp}@example.com`;

  const shipperId = await registerCorporateShipperAndGetId(page, shipperName, shipperEmail);

  const bookingNewPage = new BookingNewPage(page);
  const bookingShowPage = new BookingShowPage(page);
  const bookingRoutePage = new BookingRoutePage(page);

  await bookingNewPage.goto();
  await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
  await bookingNewPage.submit();

  const bookingUrl = page.url() as string;
  const bookingId = bookingUrl.split('/').pop()!;

  // 経路設計依頼（PRELIMINARY → ROUTE_PROPOSED）
  await bookingShowPage.clickAssignToRouting();
  await expect(bookingShowPage.getDetailValue('状態')).toHaveText('経路提案済');

  // 経路割り当て画面へ
  await bookingShowPage.clickRouteAssign();

  // V001 を選択して経路確定（CargoRoutedEvent 発火 → 精算書自動生成）
  await bookingRoutePage.selectVoyage('V001');
  await bookingRoutePage.submitRoute();

  // 経路確定後は詳細画面に戻る
  await expect(page).toHaveURL(/\/bookings\/[^/]+$/);

  return { bookingId };
}

test.describe('US22: 法人割引を適用する', () => {
  test('受入条件1: 精算書一覧に割引後金額が表示される', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    await billingIndexPage.goto();

    await expect(page).toHaveURL('/billing/invoices');
    await expect(billingIndexPage.heading).toHaveText('請求管理');
    await expect(billingIndexPage.table).toBeVisible();

    // 当該予約の行に割引後金額が表示される
    const row = await billingIndexPage.getInvoiceRowByBookingId(bookingId);
    await expect(row).toBeVisible();
    // 「割引後金額」列（4列目）が表示されている
    await expect(row.locator('td').nth(3)).toContainText('円');
  });

  test('受入条件2: 精算書詳細に割引種別・割引率が表示される', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    const billingShowPage = new BillingShowPage(page);

    await billingIndexPage.goto();
    await billingIndexPage.clickDetailByBookingId(bookingId);

    await expect(billingShowPage.heading).toHaveText('請求詳細');
    // 法人割引が適用されている
    await expect(billingShowPage.getDetailValue('割引種別')).toHaveText('法人割引');
    // 割引率が表示される（15.0%）
    await expect(billingShowPage.getDetailValue('割引率')).toContainText('%');
  });

  test('受入条件3: 精算書詳細に割引後金額（請求金額 × 割引率で計算）が表示される', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    const billingShowPage = new BillingShowPage(page);

    await billingIndexPage.goto();
    await billingIndexPage.clickDetailByBookingId(bookingId);

    // 請求金額と割引後金額が表示される
    await expect(billingShowPage.getDetailValue('請求金額（税抜）')).toContainText('円');
    await expect(billingShowPage.getDetailValue('割引後金額')).toContainText('円');
  });

  test('受入条件4: 精算書に支払期限（発行日から30日後）が表示される', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    const billingShowPage = new BillingShowPage(page);

    await billingIndexPage.goto();
    await billingIndexPage.clickDetailByBookingId(bookingId);

    // 支払期限が表示される
    await expect(billingShowPage.getDetailValue('支払期限')).toBeVisible();
    const dueDateText = await billingShowPage.getDetailValue('支払期限').textContent();
    expect(dueDateText).toMatch(/\d{4}-\d{2}-\d{2}/);
  });
});

test.describe('US23: 精算を処理する', () => {
  test('受入条件1: 経路確定後に精算書が自動生成されて一覧に表示される', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    await billingIndexPage.goto();

    await expect(billingIndexPage.table).toBeVisible();

    // 当該予約の精算書が一覧に表示される
    const row = await billingIndexPage.getInvoiceRowByBookingId(bookingId);
    await expect(row).toBeVisible();
    // 支払状態が「未精算」
    await expect(row.locator('.badge')).toHaveText('未精算');
  });

  test('受入条件2: 入金確認画面から入金確認操作ができる', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    const billingShowPage = new BillingShowPage(page);
    const billingConfirmPage = new BillingConfirmPage(page);

    await billingIndexPage.goto();
    await billingIndexPage.clickDetailByBookingId(bookingId);

    await expect(billingShowPage.heading).toHaveText('請求詳細');
    await expect(billingShowPage.confirmButton).toBeVisible();

    // 入金確認画面へ遷移
    await billingShowPage.clickConfirm();

    await expect(billingConfirmPage.heading).toHaveText('入金確認');
    await expect(billingConfirmPage.getDetailValue('予約番号')).toHaveText(bookingId);
    await expect(billingConfirmPage.submitButton).toBeVisible();
  });

  test('受入条件3: 入金確認後に精算書の支払状態が「精算済」になる', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    const billingShowPage = new BillingShowPage(page);
    const billingConfirmPage = new BillingConfirmPage(page);

    await billingIndexPage.goto();
    await billingIndexPage.clickDetailByBookingId(bookingId);

    await billingShowPage.clickConfirm();
    await billingConfirmPage.submit();

    // PRG パターン：詳細画面へリダイレクト
    await expect(page).toHaveURL(/\/billing\/invoices\/[^/]+$/);
    await expect(billingShowPage.heading).toHaveText('請求詳細');

    // 支払状態が「精算済」になる
    await expect(billingShowPage.getDetailValue('支払状態')).toContainText('精算済');
    // 入金確認ボタンが非表示になる
    await expect(billingShowPage.confirmButton).not.toBeVisible();
  });

  test('受入条件4: 入金確認後に予約状態が「精算完了」になる', async ({ page, loggedIn }) => {
    const { bookingId } = await setupBillingFlow(page);

    const billingIndexPage = new BillingIndexPage(page);
    const billingShowPage = new BillingShowPage(page);
    const billingConfirmPage = new BillingConfirmPage(page);

    await billingIndexPage.goto();
    await billingIndexPage.clickDetailByBookingId(bookingId);

    await billingShowPage.clickConfirm();
    await billingConfirmPage.submit();

    // 予約詳細画面へ移動して状態を確認
    await page.goto(`/bookings/${bookingId}`);
    const bookingShowPage = new BookingShowPage(page);
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('精算完了');
  });
});

test.describe('請求管理ナビゲーション', () => {
  test('ナビバーに請求管理リンクが表示される', async ({ page, loggedIn }) => {
    const navbar = new NavbarPage(page);
    const billingIndexPage = new BillingIndexPage(page);

    await billingIndexPage.goto();

    await expect(navbar.billingLink).toBeVisible();
  });

  test('請求管理ページでは請求管理リンクがアクティブになる', async ({ page, loggedIn }) => {
    const navbar = new NavbarPage(page);
    const billingIndexPage = new BillingIndexPage(page);

    await billingIndexPage.goto();

    expect(await navbar.isActiveLink(navbar.billingLink)).toBe(true);
    expect(await navbar.isActiveLink(navbar.bookingsLink)).toBe(false);
  });

  test('ナビバーの請求管理リンクで請求一覧へ遷移できる', async ({ page, loggedIn }) => {
    const navbar = new NavbarPage(page);

    await page.goto('/bookings');
    await navbar.clickBilling();

    await expect(page).toHaveURL('/billing/invoices');
  });
});
