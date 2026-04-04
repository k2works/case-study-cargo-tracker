import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { BookingPage } from '../pages/BookingPage';
import { RoutingPage } from '../pages/RoutingPage';
import { LoginPage } from '../pages/LoginPage';

function futureDateStr(monthsAhead: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + monthsAhead);
  return d.toISOString().slice(0, 10);
}

// ─────────────────────────────────────────────────────────────────────────────
// E33〜E35: US22 経路を選択・確定する
//
// 受入条件:
//   AC1: 経路候補一覧から 1 件を選択できる
//        — 割り当てボタンをクリックするとモーダルが開き、航海番号と推定着日が表示される
//   AC2: 選択した経路の詳細（経由港・航海番号・出発日・到着日）を確認できる
//        — モーダルの区間詳細テーブルに出発港・到着港・出発日・到着日が表示される
//        — 経由港のある候補（SG002）では複数区間が表示される
//   AC1+確定: モーダルで確定すると経路が予約に保存される
// ─────────────────────────────────────────────────────────────────────────────
test.describe.serial('E33〜E35: US22 経路を選択・確定する', () => {
  let bookingId = '';

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');

    const shipperPage = new ShipperPage(page);
    await shipperPage.registerIndividual(
      'US22 経路選択テスト荷主',
      `us22-route-selection-${Date.now()}@example.com`,
    );
    const shipperId = await shipperPage.extractShipperId();

    const bookingPage = new BookingPage(page);
    await bookingPage.register({
      shipperId,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500',
      quantity: '1',
      originLocation: 'JPTYO',
      destinationLocation: 'SGSIN',
      requestedPickupDate: futureDateStr(1),
      requestedDeliveryDate: futureDateStr(6),
    });
    bookingId = await bookingPage.extractBookingId();

    await context.close();
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E33: 割り当てボタンをクリックするとモーダルが開き基本情報が表示される（AC1）
  // ─────────────────────────────────────────────────────────────────────────
  test('E33: 割り当てボタンをクリックするとモーダルが開き航海番号と推定着日が表示される', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // 候補が表示されている
    expect(await routingPage.countCandidates()).toBeGreaterThan(0);

    // SG001 の割り当てボタンをクリックしてモーダルを開く
    const modal = await routingPage.openAssignModal(0);

    // AC1: モーダルが開いて航海番号（SG001）が表示される
    await expect(routingPage.modalVoyageNumber).toHaveText('SG001');

    // 推定着日が YYYY-MM-DD 形式で表示される
    await expect(routingPage.modalEstimatedArrival).toContainText(/\d{4}-\d{2}-\d{2}/);

    // モーダルタイトルが表示されている
    await expect(modal.locator('.modal-title')).toContainText('経路割り当ての確認');
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E34: モーダルの区間詳細テーブルに出発港・到着港・出発日・到着日が表示される（AC2）
  // ─────────────────────────────────────────────────────────────────────────
  test('E34: 区間詳細テーブルに出発港・到着港・出発日・到着日が表示される（SG001 直行便）', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // SG001 の割り当てボタンをクリック
    await routingPage.openAssignModal(0);

    // AC2: fetch 完了後に区間詳細テーブルが表示される
    const table = await routingPage.waitForLegsTable();
    await expect(table).toBeVisible();

    // テーブルヘッダーに「出発港」「到着港」「出発日」「到着日」が含まれる
    await expect(table).toContainText('出発港');
    await expect(table).toContainText('到着港');
    await expect(table).toContainText('出発日');
    await expect(table).toContainText('到着日');

    // SG001 は JPTYO → SGSIN の直行便 — 1 区間表示される
    const rows = routingPage.legsTableRows;
    expect(await rows.count()).toBe(1);

    const firstRow = rows.first();
    await expect(firstRow).toContainText('JPTYO');    // 出発港
    await expect(firstRow).toContainText('SGSIN');    // 到着港
    await expect(firstRow).toContainText(/\d{4}-\d{2}-\d{2}/); // 出発日
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E34b: 経由港ありの候補（SG002）では複数区間が表示される（AC2）
  // ─────────────────────────────────────────────────────────────────────────
  test('E34b: 経由港あり（SG002）の場合は複数区間が区間詳細テーブルに表示される', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // SG002（2 番目の候補、釜山経由）の割り当てボタンをクリック
    await routingPage.openAssignModal(1);

    // fetch 完了後にテーブルが表示される
    const table = await routingPage.waitForLegsTable();
    await expect(table).toBeVisible();

    // SG002 は JPTYO → KRPUS → SGSIN の 2 区間
    const rows = routingPage.legsTableRows;
    expect(await rows.count()).toBe(2);

    // 1 区間目: JPTYO → KRPUS（経由港: 釜山）
    await expect(rows.nth(0)).toContainText('JPTYO');
    await expect(rows.nth(0)).toContainText('KRPUS');

    // 2 区間目: KRPUS → SGSIN
    await expect(rows.nth(1)).toContainText('KRPUS');
    await expect(rows.nth(1)).toContainText('SGSIN');
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E35: モーダルで確定すると経路が保存され予約詳細に反映される（AC1 + 確定）
  // ─────────────────────────────────────────────────────────────────────────
  test('E35: モーダルで経路を確定すると予約に保存され予約詳細に航海番号が表示される', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoByBookingId(bookingId);

    // 最初の候補（SG001）を割り当てる
    await routingPage.assignRoute(0);

    // 予約詳細ページにリダイレクトされる
    await expect(page).toHaveURL(`/bookings/${bookingId}`);

    // 保存された航海番号（SG001）と経路パスが表示される
    await expect(page.locator('body')).toContainText('SG001');
    await expect(page.locator('body')).toContainText('JPTYO');
    await expect(page.locator('body')).toContainText('SGSIN');
  });
});
