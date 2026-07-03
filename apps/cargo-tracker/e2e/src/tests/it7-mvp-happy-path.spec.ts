import { test, expect, Page } from '@playwright/test';

/**
 * IT7 T6-01: Release 1.0 MVP 統合ハッピーパス
 *
 * 予約 → 経路 → 追跡 → 荷役 → 引取 → 料金 の一連業務フローを 1 本の E2E で通す。
 * IT1-IT6 の個別スペック (booking-registration / voyage-registration / it3-stories /
 * pricing-calculation / notifications) が個別スライスを覆う一方、統合パスは未整備。
 * 本スペックは Ralph Loop iteration 2 で骨組みを確立し、以降の反復で完成に向けて拡張する。
 *
 * 実装状態:
 * - Stage 1-3 (Shipper → Voyage → Booking) は他スペックで検証済のパターンを再利用し実行可能
 * - Stage 4 (Route 割当 → Confirm → TrackingIssue) は POST /bookings/{id}/submit /
 *   handover / route / confirm の連鎖が必要。次反復で有効化
 * - Stage 5 (Handling 登録) と Stage 6 (Claim) は confirmation_code の発行タイミング
 *   と連動するため、Stage 4 完了後に接続する
 * - Stage 7 (Pricing 独立検証) は既存 pricing-calculation.spec の内容と同等、smoke 確認
 */

const PORT_FROM = 'JPYOK'; // 横浜 (IT7 用に他スペックと衝突しないペアを選択)
const PORT_TO = 'DEHAM'; // ハンブルグ

async function registerShipperFlow(page: Page): Promise<string> {
  const email = `it7.mvp.${Date.now()}.${Math.random().toString(36).slice(2, 6)}@example.com`;
  await page.goto('/shippers/new');
  await page.locator('#name').fill('IT7 MVP ハッピーパス荷主');
  await page.locator('#email').fill(email);
  await page.locator('#address').fill('Yokohama Port District');
  await page.locator('#kind-individual').check();
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/shippers\/SHP-[A-Z0-9]{6}$/);
  return page.url().split('/').pop()!;
}

async function registerVoyageFlow(
  page: Page,
  from: string,
  to: string,
  depart: string,
  arrive: string,
): Promise<string> {
  const voyageNumber = `V${Date.now().toString().slice(-6)}${Math.floor(Math.random() * 10)}`;
  await page.goto('/voyages/new');
  await page.locator('#voyageNumber').fill(voyageNumber);
  await page.locator('select[name="movement1Departure"]').selectOption(from);
  await page.locator('select[name="movement1Arrival"]').selectOption(to);
  await page.locator('input[name="movement1DepartureTime"]').fill(depart);
  await page.locator('input[name="movement1ArrivalTime"]').fill(arrive);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}$`));
  return voyageNumber;
}

async function registerBookingFlow(
  page: Page,
  shipperId: string,
  deadline: string,
): Promise<string> {
  await page.goto('/bookings/new');
  await page.locator('#shipperId').fill(shipperId);
  await page.locator('#origin').selectOption(PORT_FROM);
  await page.locator('#destination').selectOption(PORT_TO);
  await page.locator('#deadline').fill(deadline);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/bookings\/BK-[A-Z0-9]{6}$/);
  return page.url().split('/').pop()!;
}

test.describe('IT7 T6-01: Release 1.0 MVP 統合ハッピーパス', () => {
  test('Stage 1-3: Shipper → Voyage → Booking を通せて予約詳細に Draft が表示される', async ({ page }) => {
    // Stage 1: 荷主登録
    const shipperId = await registerShipperFlow(page);

    // Stage 2: 航海登録 (期日内に到着する Voyage を先に用意)
    const voyageNumber = await registerVoyageFlow(
      page,
      PORT_FROM,
      PORT_TO,
      '2027-01-10T09:00',
      '2027-02-05T18:00',
    );

    // Stage 3: 予約登録
    const bookingId = await registerBookingFlow(page, shipperId, '2027-02-28T18:00');

    // 予約詳細に Draft と shipperId が表示される
    const body = page.locator('body');
    await expect(body).toContainText('Draft');
    await expect(body).toContainText(shipperId);

    // 経路候補画面に voyage が現れる (US08a と同等の検証)
    await page.goto(`/bookings/${bookingId}/routes`);
    await expect(page.locator('body')).toContainText(voyageNumber);
  });

  test('Stage 4: 予約 submit → handover → route 割当 → confirm → TrackingIssue', async ({ page }) => {
    // Stage 1-3 の再構築
    const shipperId = await registerShipperFlow(page);
    await registerVoyageFlow(page, PORT_FROM, PORT_TO, '2027-01-15T09:00', '2027-02-10T18:00');
    const bookingId = await registerBookingFlow(page, shipperId, '2027-02-28T18:00');
    const detailUrl = `/bookings/${bookingId}`;

    // Stage 4a: submit (Draft → Submitted)
    const submitRes = await page.request.post(`${detailUrl}/submit`, { maxRedirects: 0 });
    expect(submitRes.status()).toBe(303);
    expect(submitRes.headers()['location']).toContain('flash=submit-ok');

    // Stage 4b: handover (Submitted → RouteProposed)
    const handoverRes = await page.request.post(`${detailUrl}/handover`, { maxRedirects: 0 });
    expect(handoverRes.status()).toBe(303);
    expect(handoverRes.headers()['location']).toContain('flash=handover-ok');

    // Stage 4c: route (RouteProposed → RouteAssigned)
    const routeRes = await page.request.post(`${detailUrl}/route`, { maxRedirects: 0 });
    expect(routeRes.status()).toBe(303);
    expect(routeRes.headers()['location']).toContain('flash=link-ok');

    // Stage 4d: confirm (RouteAssigned → Confirmed + TrackingNumber 発行)
    const confirmRes = await page.request.post(`${detailUrl}/confirm`, { maxRedirects: 0 });
    expect(confirmRes.status()).toBe(303);
    expect(confirmRes.headers()['location']).toContain('flash=confirm-ok');

    // 詳細ページに Confirmed 状態と追跡番号が表示される
    await page.goto(detailUrl);
    const body = page.locator('body');
    await expect(body).toContainText('Confirmed');
    // 追跡番号は 8 文字英数大文字 (TrackingNumber 規約)
    await expect(body).toContainText(/[A-Z0-9]{8}/);
  });

  test.fixme('Stage 5-6: Handling 登録 → Claim (確認コード) → Tracking が TsClaimed に遷移', async ({ page }) => {
    // Ralph Loop 次反復で有効化する。confirmation_code の発行タイミング
    // (現行実装は AwaitingClaim 遷移時に code_hash を保存) の把握が必要。
    //
    // 想定フロー:
    //   POST /handling/new (RECEIVE) → PRG
    //   POST /handling/new (LOAD)    → PRG
    //   POST /handling/new (UNLOAD)  → PRG → Tracking が AwaitingClaim へ
    //                                          + confirmation_code 生成
    //   (テスト API 経由で平文 code を取得)
    //   POST /handling/claim         → 確認コード検証成功 → TsClaimed
    //   GET /public/tracking/{tn}    → 詳細ページに Claimed 表示
    //   GET /notifications?bookingId={bookingId} → Sent 通知が 1 件見える
  });

  test('Stage 7: /pricing/calculate 単体スモーク (独立検証)', async ({ page }) => {
    // Pricing BC は Booking/Tracking と独立し、任意タイミングで料金算出可能。
    // 統合ハッピーパスの締めくくりとして正常算出を確認する。
    await page.goto('/pricing/calculate');
    await page.locator('#cargoCategory').selectOption('General');
    await page.locator('#distanceKm').fill('9000');
    await page.locator('#weightKg').fill('300');
    await page.locator('#baseCurrency').fill('JPY');
    await page.locator('#targetCurrency').fill('JPY');
    await page.locator('#discountRate').fill('0');
    await page.locator('button[type="submit"]').click();

    await expect(page.getByTestId('calc-result-success')).toBeVisible();
    await expect(page.getByTestId('calc-result-success')).toContainText('JPY');
  });
});
