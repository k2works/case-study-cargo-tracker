import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { VoyagePage } from '../pages/VoyagePage';
import { RouteCandidatePage } from '../pages/RouteCandidatePage';
import { TrackingPage } from '../pages/TrackingPage';
import { LoginPage } from '../pages/LoginPage';

/** US18 追跡情報照会 E2E。
 *  - 認証ユーザー: /tracking フォーム → /tracking/:trackingNumber 詳細
 *  - 未認証ユーザー: /public/tracking/:trackingNumber（ログイン不要）
 *  - 存在しない番号: 404 + 「追跡番号が見つかりません」
 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

async function issueTracking(page: import('@playwright/test').Page): Promise<string> {
  const suffix = uniqueSuffix();
  const voyage = new VoyagePage(page);
  await voyage.gotoNew();
  await voyage.fillRegister({
    voyageNumber: `VY-US18-${suffix}`,
    departureLocation: 'JPYOK',
    arrivalLocation: 'USNYC',
    departureTime: '2099-07-01T10:00',
    arrivalTime: '2099-07-10T18:00',
  });
  await voyage.submitRegister();

  const shipper = new ShipperPage(page);
  await shipper.gotoNew();
  await shipper.fillForm({
    name: 'US18 テスト荷主',
    email: `us18-${suffix}@example.com`,
    phone: '03-0000-0000',
    address: '東京都',
    shipperType: 'Individual',
  });
  await shipper.submit();
  await shipper.gotoList();
  const shipperId = ((await page.locator('tr', { hasText: `us18-${suffix}@example.com` }).textContent()) ?? '').match(/SH-\S+/)?.[0]!;

  const booking = new BookingPage(page);
  await booking.gotoNew();
  await booking.fillForm({
    shipperCode: shipperId,
    origin: 'JPYOK',
    destination: 'USNYC',
    arrivalDeadline: futureDate(365 * 70),
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

  return ((await page.locator('.alert-primary').textContent()) ?? '').match(/TN-\d+/)?.[0]!;
}

test.describe('US18 追跡情報照会（認証ユーザー）', () => {
  test('/tracking から追跡番号を入力 → 詳細画面に状態とイベント履歴が表示される', async ({
    page,
    loggedIn,
  }) => {
    const trackingNumber = await issueTracking(page);

    const tracking = new TrackingPage(page);
    await tracking.gotoInput();
    await tracking.lookup(trackingNumber);
    await expect(page).toHaveURL(`/tracking/${trackingNumber}`);
    await expect(page.locator('body')).toContainText(trackingNumber);
    await expect(page.locator('.badge')).toContainText('NotReceived');
    await expect(page.locator('#tracking-timeline')).toBeVisible();
  });

  test('存在しない追跡番号は /tracking に戻り error フラッシュ', async ({ page, loggedIn }) => {
    const tracking = new TrackingPage(page);
    await tracking.gotoInput();
    await tracking.lookup('TN-999999');
    await expect(page).toHaveURL('/tracking');
    await expect(page.locator('.alert-danger')).toContainText('見つかりません');
  });

  test('空入力で送信すると error フラッシュ', async ({ page, loggedIn }) => {
    const tracking = new TrackingPage(page);
    await tracking.gotoInput();
    // HTML required を bypass するためコード経由で送信
    await page.locator('input[name="trackingNumber"]').evaluate((el) => (el as HTMLInputElement).removeAttribute('required'));
    await page.locator('button[type="submit"]:has-text("照会")').click();
    await expect(page.locator('.alert-danger')).toContainText('追跡番号を入力');
  });
});

test.describe('US18 公開追跡照会（未認証）', () => {
  test('ログアウト状態で /public/tracking/:trackingNumber にアクセスすると 200 で詳細が表示される', async ({
    page,
    loggedIn,
  }) => {
    const trackingNumber = await issueTracking(page);

    // 一度ログアウトしてセッションをクリア
    await page.locator('form[action="/logout"] button[type="submit"]').click();
    await expect(page).toHaveURL(/\/login/);

    const tracking = new TrackingPage(page);
    await tracking.gotoPublic(trackingNumber);
    await expect(page).toHaveURL(`/public/tracking/${trackingNumber}`);
    await expect(page.locator('body')).toContainText(trackingNumber);
    await expect(page.locator('body')).toContainText('NotReceived');
  });

  test('未認証で存在しない追跡番号は 404 + 「追跡番号が見つかりません」', async ({ page }) => {
    const response = await page.goto('/public/tracking/TN-999999');
    expect(response?.status()).toBe(404);
    await expect(page.locator('body')).toContainText('追跡番号が見つかりません');
  });

  test('認証必須画面 /tracking/:trackingNumber は未認証で /login へリダイレクト', async ({ page }) => {
    const response = await page.goto('/tracking/TN-000001');
    expect(response?.status()).toBe(200); // login page で 200
    await expect(page).toHaveURL(/\/login/);
  });
});
