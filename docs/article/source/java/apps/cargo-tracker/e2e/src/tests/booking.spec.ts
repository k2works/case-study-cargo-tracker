import { test, expect } from '../fixtures';
import { ShipperNewPage } from '../pages/ShipperPage';
import { BookingIndexPage, BookingNewPage, BookingShowPage, BookingRoutePage } from '../pages/BookingPage';
import { NavbarPage } from '../pages/NavbarPage';

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

  test('予約を経路設計者に引き渡せる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `引き渡しテスト荷主 ${timestamp}`, `routing-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USLAX', '2027-12-31');
    await bookingNewPage.submit();

    // 仮受付状態であることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('仮受付');
    await expect(bookingShowPage.routingButton).toBeVisible();

    // 経路設計依頼操作
    await bookingShowPage.clickAssignToRouting();

    // 状態が「経路提案済」になっていることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('経路提案済');
    await expect(bookingShowPage.successAlert).toBeVisible();
    // 引き渡し後は経路設計依頼ボタンが非表示になる
    await expect(bookingShowPage.routingButton).not.toBeVisible();
  });

  test('予約登録フォームから一覧へ戻れる', async ({ page, loggedIn }) => {
    const bookingNewPage = new BookingNewPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.backButton.click();

    await expect(page).toHaveURL('/bookings');
  });
});

test.describe('US09: 経路を選択・確定する', () => {
  async function setupBookingInRouteProposed(page: any): Promise<{ bookingShowPage: BookingShowPage, bookingRoutePage: BookingRoutePage }> {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `US09テスト荷主 ${timestamp}`, `us09-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();
    // V001(JPTYO→USNYC), V002(JPTYO→CNSHA→USNYC) が検索できる条件
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
    await bookingNewPage.submit();

    // PRELIMINARY → ROUTE_PROPOSED（経路設計依頼）
    await bookingShowPage.clickAssignToRouting();
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('経路提案済');

    return { bookingShowPage, bookingRoutePage: new BookingRoutePage(page) };
  }

  test('受入条件1: 経路候補一覧（航海番号・出発地・到着地・所要日数）を確認できる', async ({ page, loggedIn }) => {
    const { bookingShowPage, bookingRoutePage } = await setupBookingInRouteProposed(page);

    await bookingShowPage.clickRouteAssign();

    await expect(bookingRoutePage.heading).toContainText('経路割り当て');
    await expect(bookingRoutePage.voyagesTable).toBeVisible();
    // 航海番号が表示されることを確認
    await expect(page.locator('label', { hasText: 'V001' })).toBeVisible();
    // 出発地・到着地が表示されることを確認
    await expect(page.locator('td', { hasText: 'JPTYO' }).first()).toBeVisible();
    await expect(page.locator('td', { hasText: 'USNYC' }).first()).toBeVisible();
    // 所要日数が表示されることを確認
    await expect(page.locator('td', { hasText: '日' }).first()).toBeVisible();
  });

  test('受入条件2: 最適な経路候補を1件選択できる', async ({ page, loggedIn }) => {
    const { bookingShowPage, bookingRoutePage } = await setupBookingInRouteProposed(page);

    await bookingShowPage.clickRouteAssign();

    // ラジオボタンで V001 を選択できることを確認
    await bookingRoutePage.selectVoyage('V001');
    await expect(page.locator('input[type="radio"][value="V001"]')).toBeChecked();
  });

  test('受入条件3: 選択後、経路が確定されて詳細画面に戻る', async ({ page, loggedIn }) => {
    const { bookingShowPage, bookingRoutePage } = await setupBookingInRouteProposed(page);

    await bookingShowPage.clickRouteAssign();
    await bookingRoutePage.selectVoyage('V001');
    await bookingRoutePage.submitRoute();

    // PRG パターン：詳細画面へリダイレクトされて成功メッセージが表示される
    await expect(page).toHaveURL(/\/bookings\/[^/]+$/);
    await expect(bookingShowPage.successAlert).toBeVisible();
    await expect(bookingShowPage.successAlert).toContainText('経路を割り当てました');
  });

  test('受入条件4: 候補がない場合、経路条件調整（US10）に進める', async ({ page, loggedIn }) => {
    const { bookingShowPage, bookingRoutePage } = await setupBookingInRouteProposed(page);

    await bookingShowPage.clickRouteAssign();

    // 存在しない条件で再検索 → 候補なし → 検索フォームで条件変更できる
    await bookingRoutePage.reSearch('ZZZXX', 'YYYWW', '2020-01-01');
    await expect(bookingRoutePage.noResultsMessage).toBeVisible();
    await expect(bookingRoutePage.noResultsMessage).toContainText('条件に合致する航路が見つかりません');
    // 検索フォームは引き続き操作可能
    await expect(bookingRoutePage.searchButton).toBeVisible();
  });
});

test.describe('US10: 経路条件を調整して再算出する', () => {
  async function goToRoutePage(page: any): Promise<BookingRoutePage> {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `US10テスト荷主 ${timestamp}`, `us10-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
    await bookingNewPage.submit();
    await bookingShowPage.clickAssignToRouting();
    await bookingShowPage.clickRouteAssign();

    return new BookingRoutePage(page);
  }

  test('受入条件1: 検索条件（期限・出発地・目的地）を変更して再検索できる', async ({ page, loggedIn }) => {
    const routePage = await goToRoutePage(page);

    // 検索フォームが表示されていることを確認
    await expect(routePage.originInput).toBeVisible();
    await expect(routePage.destinationInput).toBeVisible();
    await expect(routePage.arrivalDeadlineInput).toBeVisible();
    await expect(routePage.searchButton).toBeVisible();

    // 条件を変更して再検索
    await routePage.reSearch('JPTYO', 'USLAX', '2027-12-31');
    await expect(page).toHaveURL(/\/bookings\/.+\/route\?/);
  });

  test('受入条件2: 変更後の条件で経路候補が再算出されて表示される', async ({ page, loggedIn }) => {
    const routePage = await goToRoutePage(page);

    // JPTYO→USLAX に条件変更（V003 が表示される）
    await routePage.reSearch('JPTYO', 'USLAX', '2027-12-31');
    await expect(routePage.voyagesTable).toBeVisible();
    await expect(page.locator('label', { hasText: 'V003' })).toBeVisible();
  });

  test('受入条件3: 条件変更後も所要日数昇順で候補が表示される', async ({ page, loggedIn }) => {
    const routePage = await goToRoutePage(page);

    // JPTYO→USNYC（V001: 31日, V002: 36日）
    await expect(routePage.voyagesTable).toBeVisible();
    const rows = page.locator('table tbody tr');
    const firstRowVoyageLabel = rows.nth(0).locator('label');
    const secondRowVoyageLabel = rows.nth(1).locator('label');
    // 最初の行が V001（所要日数が短い方）
    await expect(firstRowVoyageLabel).toHaveText('V001');
    await expect(secondRowVoyageLabel).toHaveText('V002');
  });

  test('受入条件4: 条件を満たす候補がない場合、その旨が表示される', async ({ page, loggedIn }) => {
    const routePage = await goToRoutePage(page);

    // 存在しない出発地で検索
    await routePage.reSearch('ZZZXX', 'YYYWW', '2020-01-01');
    await expect(routePage.noResultsMessage).toBeVisible();
    await expect(routePage.noResultsMessage).toContainText('条件に合致する航路が見つかりません');
    await expect(routePage.voyagesTable).not.toBeVisible();
  });
});

test.describe('US11: 経路情報を予約に紐付ける', () => {
  test('受入条件1: 確定経路と予約番号を確認できる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `US11テスト荷主 ${timestamp}`, `us11-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);
    const bookingRoutePage = new BookingRoutePage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
    await bookingNewPage.submit();
    await bookingShowPage.clickAssignToRouting();

    await bookingShowPage.clickRouteAssign();

    // 経路割り当て画面で予約番号と経路候補を確認できる
    await expect(page).toHaveURL(/\/bookings\/.+\/route/);
    // ページ上部（h1 の次）に予約番号が表示される
    await expect(page.locator('main .d-flex p.text-secondary')).toContainText('予約番号');
    // 航路候補が表示される
    await expect(bookingRoutePage.voyagesTable).toBeVisible();
  });

  test('受入条件2: 経路情報を予約に紐付ける操作を実行できる', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `US11紐付けテスト荷主 ${timestamp}`, `us11-link-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);
    const bookingRoutePage = new BookingRoutePage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
    await bookingNewPage.submit();
    await bookingShowPage.clickAssignToRouting();
    await bookingShowPage.clickRouteAssign();

    // 経路を選択して紐付け操作を実行
    await bookingRoutePage.selectVoyage('V001');
    await bookingRoutePage.submitRoute();

    // PRG 後に詳細画面へ戻る
    await expect(page).toHaveURL(/\/bookings\/[^/]+$/);
    await expect(bookingShowPage.successAlert).toBeVisible();
  });

  test('受入条件3: 紐付け後、予約状態が「経路提案済」（ROUTE_PROPOSED）に更新される', async ({ page, loggedIn }) => {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `US11状態テスト荷主 ${timestamp}`, `us11-status-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);
    const bookingRoutePage = new BookingRoutePage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
    await bookingNewPage.submit();
    await bookingShowPage.clickAssignToRouting();
    await bookingShowPage.clickRouteAssign();
    await bookingRoutePage.selectVoyage('V001');
    await bookingRoutePage.submitRoute();

    // 紐付け後の状態が「経路提案済」であることを確認
    await expect(bookingShowPage.getDetailValue('状態')).toHaveText('経路提案済');
  });
});

test.describe('経路割り当て画面のナビゲーション', () => {
  async function openRoutePage(page: any): Promise<{ bookingShowPage: BookingShowPage, bookingId: string }> {
    const timestamp = Date.now();
    const shipperId = await registerShipperAndGetId(page, `ナビテスト荷主 ${timestamp}`, `nav-route-${timestamp}@example.com`);

    const bookingNewPage = new BookingNewPage(page);
    const bookingShowPage = new BookingShowPage(page);

    await bookingNewPage.goto();
    await bookingNewPage.fill(shipperId, 'GENERAL', '10.0', 'JPTYO', 'USNYC', '2027-12-31');
    await bookingNewPage.submit();

    const url = page.url() as string;
    const bookingId = url.split('/').pop()!;

    await bookingShowPage.clickAssignToRouting();
    await bookingShowPage.clickRouteAssign();

    return { bookingShowPage, bookingId };
  }

  test('経路割り当て画面で共通ナビバーが表示される', async ({ page, loggedIn }) => {
    const navbar = new NavbarPage(page);
    await openRoutePage(page);

    await expect(navbar.brand).toBeVisible();
    await expect(navbar.bookingsLink).toBeVisible();
    await expect(navbar.shippersLink).toBeVisible();
    await expect(navbar.logoutButton).toBeVisible();
  });

  test('経路割り当て画面では予約管理リンクがアクティブである', async ({ page, loggedIn }) => {
    const navbar = new NavbarPage(page);
    await openRoutePage(page);

    expect(await navbar.isActiveLink(navbar.bookingsLink)).toBe(true);
    expect(await navbar.isActiveLink(navbar.shippersLink)).toBe(false);
  });

  test('経路割り当て画面から「詳細へ戻る」リンクで予約詳細に戻れる', async ({ page, loggedIn }) => {
    const { bookingId } = await openRoutePage(page);
    const bookingRoutePage = new BookingRoutePage(page);

    await bookingRoutePage.backToDetailLink.click();
    await expect(page).toHaveURL(`/bookings/${bookingId}`);
  });

  test('経路割り当て画面から予約管理ナビリンクで予約一覧へ遷移できる', async ({ page, loggedIn }) => {
    const navbar = new NavbarPage(page);
    await openRoutePage(page);

    await navbar.clickBookings();
    await expect(page).toHaveURL('/bookings');
  });

  test('経路割り当て画面から荷主管理ナビリンクで荷主一覧へ遷移できる', async ({ page, loggedIn }) => {
    const navbar = new NavbarPage(page);
    await openRoutePage(page);

    await navbar.clickShippers();
    await expect(page).toHaveURL('/shippers');
  });
});
