import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { VoyagePage } from '../pages/VoyagePage';
import { RouteCandidatePage } from '../pages/RouteCandidatePage';
import { HandlingPage } from '../pages/HandlingPage';
import { TrackingPage } from '../pages/TrackingPage';

/** IT5 荷主中核体験 E2E。
 *  予約 → 経路設計 → 経路確定 → 予約確定 → 追跡番号発行
 *  → 荷役（Receive → Load）→ 追跡照会（認証 + 公開）の一気通貫。
 *  IT5 計画 [Definition of Done] の業務導線 E2E 緑を担保する。
 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

test.describe('IT5 業務導線（荷主中核体験）', () => {
  test('予約 → 経路確定 → 予約確定 → 追跡番号発行 → 荷役 → 追跡照会（認証 + 公開）が一気通貫で動作する', async ({
    page,
    loggedIn,
  }) => {
    const suffix = uniqueSuffix();
    const voyageNumber = `VY-IT5-${suffix}`;

    // 1. voyage 登録（US24）
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

    // 2. 荷主登録（US02）
    const shipper = new ShipperPage(page);
    await shipper.gotoNew();
    await shipper.fillForm({
      name: 'IT5 統合荷主',
      email: `it5-${suffix}@example.com`,
      phone: '03-0000-0000',
      address: '東京都',
      shipperType: 'Individual',
    });
    await shipper.submit();
    await shipper.gotoList();
    const shipperId = ((await page.locator('tr', { hasText: `it5-${suffix}@example.com` }).textContent()) ?? '').match(/SH-\S+/)?.[0]!;

    // 3. 予約（US04）→ 引き渡し（US06）
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

    // 4. 経路確定（US09 / US11）
    const routes = new RouteCandidatePage(page);
    await routes.gotoCandidates(bookingId);
    await routes.confirmCandidate(0);

    // 5. 予約確定（US13）
    await booking.gotoDetail(bookingId);
    await page.locator('button[data-action="confirm"]').click();
    await expect(page.locator('.badge')).toContainText('Confirmed');

    // 6. 追跡番号発行（US14）
    await page.locator('button[data-action="issue-tracking"]').click();
    await expect(page.locator('.badge')).toContainText('TrackingIssued');
    const trackingNumber = ((await page.locator('.alert-primary').textContent()) ?? '').match(/TN-\d+/)?.[0]!;
    expect(trackingNumber).toMatch(/^TN-\d+$/);

    // 7. 荷役記録（US15）Receive → Load
    const handling = new HandlingPage(page);
    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Receive',
      completionDateTime: '2099-07-01T08:00',
      locationUnLocode: 'JPYOK',
      operatorName: 'IT5 統合作業員',
    });
    await handling.submit();
    await expect(page.locator('.alert-success')).toContainText(trackingNumber);

    await handling.gotoNew();
    await handling.fillForm({
      trackingNumber,
      eventType: 'Load',
      completionDateTime: '2099-07-01T12:00',
      locationUnLocode: 'JPYOK',
      voyageNumber,
    });
    await handling.submit();

    // 8. 認証ユーザー追跡照会（US18）→ Loaded
    const tracking = new TrackingPage(page);
    await tracking.gotoInput();
    await tracking.lookup(trackingNumber);
    await expect(page).toHaveURL(`/tracking/${trackingNumber}`);
    await expect(page.locator('.badge')).toContainText('Loaded');
    await expect(page.locator('body')).toContainText('Receive');
    await expect(page.locator('body')).toContainText('Load');

    // 9. 通知ログに 4 種類記録されていることを確認（RouteNotified は notify 未実行のためなし）
    await page.goto(`/bookings/${bookingId}/notifications`);
    await expect(page.locator('body')).toContainText('BookingConfirmed');
    await expect(page.locator('body')).toContainText('TrackingIssued');
    await expect(page.locator('body')).toContainText('HandlingRecorded');

    // 10. ログアウト → 公開追跡照会（US18）
    await page.locator('form[action="/logout"] button[type="submit"]').click();
    await expect(page).toHaveURL(/\/login/);
    await tracking.gotoPublic(trackingNumber);
    await expect(page.locator('body')).toContainText(trackingNumber);
    await expect(page.locator('body')).toContainText('Loaded');
  });
});
