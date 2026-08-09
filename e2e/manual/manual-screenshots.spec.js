import { test, expect } from '@playwright/test';
import { localDate, localDateTime } from '../app/support/time.js';
import path from 'path';

/**
 * ユーザーマニュアル（docs/manual/）の画面キャプチャを生成する。
 *
 * **これは検証ではなくドキュメント生成である。** アサーションは
 * 「撮影対象が描画し終わったこと」を待つためだけに置いている。
 * 待たずに撮ると描画途中が写る。
 *
 * 実在の取引先情報は載せない。データはシードユーザーと、
 * ここで登録するダミー荷主のみとする。
 */

const ASSETS = path.join(process.cwd(), 'docs', 'manual', 'assets');

/** シードされた営業担当者。ロール別の表示を撮るために使う。 */
const SALES = { username: 'sales', password: 'password' };

/**
 * 動作確認用の荷主。**アプリ側のシード（db/demo/V900__demo_shipper.sql）と同じ内容**である。
 *
 * ここで登録し直さずシードを使うのは、テストの実行順に依存させないためと、
 * マニュアルの図・開発環境の画面・キャプチャの 3 者を 1 つの出所に揃えるためである。
 * 実在の企業・個人は使わない。
 */
const DEMO_SHIPPER = {
  code: 'SHP-000001',
  name: '山田商事',
  email: 'shipper-sample@example.com',
};

/**
 * 指定した利用者でログインし、ダッシュボードが表示されるまで待つ.
 * @param {import('@playwright/test').Page} page ページ
 * @param {{username: string, password: string}} user 利用者
 */
async function login(page, user) {
  await page.goto('/login');
  await page.fill('#username', user.username);
  await page.fill('#password', user.password);
  await page.click('button[type="submit"]');
  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
}

/**
 * ページ全体を `docs/manual/assets/` に保存する.
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} name `<章番号>-<英字スラッグ>.png`
 */
async function capture(page, name) {
  await page.screenshot({ path: path.join(ASSETS, name), fullPage: true });
}

test('02-login（ログイン画面）', async ({ page }) => {
  // **本番と同じ見た目で撮る。** 開発環境の事前入力ブロックが写った図を代表に置くと、
  // 業務担当者は自分の画面と一致しない図を見ることになる。
  // 事前入力の画面は 02-login-dev.png として別に用意する
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible();
  await page.evaluate(() => {
    document.querySelectorAll('.alert-warning, .card').forEach((el) => el.remove());
    const main = document.querySelector('main');
    if (main) {
      main.setAttribute('style', 'max-width: 24rem; padding-top: 6rem;');
    }
    const username = document.getElementById('username');
    const password = document.getElementById('password');
    if (username) {
      username.value = '';
    }
    if (password) {
      password.value = '';
    }
  });
  await capture(page, '02-login.png');
});

test('02-login-dev（開発環境のログイン画面）', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: '動作確認用の利用者' })).toBeVisible();
  await capture(page, '02-login-dev.png');
});

test('02-login-error（認証エラーの表示）', async ({ page }) => {
  await page.goto('/login');
  await page.fill('#username', SALES.username);
  await page.fill('#password', 'wrong-password');
  await page.click('button[type="submit"]');
  await expect(page.getByText('利用者 ID またはパスワードが正しくありません')).toBeVisible();
  await capture(page, '02-login-error.png');
});

test('02-dashboard（ダッシュボード）', async ({ page }) => {
  await login(page, SALES);
  await expect(page.getByRole('link', { name: '荷主管理' }).first()).toBeVisible();
  await capture(page, '02-dashboard.png');
});

test('03-shipper-form（荷主登録）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers/new');
  await expect(page.getByRole('heading', { name: '荷主登録' })).toBeVisible();
  await capture(page, '03-shipper-form.png');
});

test('03-shipper-detail（荷主詳細）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers');
  // 一覧の「詳細」から開く。読者がマニュアルどおりに辿る経路と同じにする
  await page.getByRole('link', { name: '詳細' }).first().click();
  await expect(page.getByRole('heading', { name: '荷主詳細' })).toBeVisible();
  await expect(page.getByText(DEMO_SHIPPER.code)).toBeVisible();
  await capture(page, '03-shipper-detail.png');
});

test('03-shipper-list（荷主一覧）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers');
  // 空状態ではなく、代表的な 1 件が表示された状態で撮る
  await expect(page.getByText(DEMO_SHIPPER.name)).toBeVisible();
  await capture(page, '03-shipper-list.png');
});

test('03-shipper-duplicate（メールアドレスの重複）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers/new');
  await page.check('#typeIndividual');
  await page.fill('#name', DEMO_SHIPPER.name);
  // シード済みのメールアドレスを入力して重複を再現する
  await page.fill('#email', DEMO_SHIPPER.email);
  await page.fill('#addressCountry', 'JP');
  await page.fill('#addressPostalCode', '100-0001');
  await page.fill('#addressRegion', '東京都');
  await page.fill('#addressCity', '千代田区');
  // ナビゲーションの「ログアウト」も submit ボタンである。名前で特定する
  await page.getByRole('button', { name: '登録' }).click();
  await expect(page.getByText('既に登録')).toBeVisible();
  await capture(page, '03-shipper-duplicate.png');
});

/**
 * 動作確認用の貨物予約。**アプリ側のシード（db/demo/V901__demo_booking.sql）と同じ内容**である。
 */
const DEMO_BOOKING = {
  origin: 'JPOSA',
  destination: 'USLAX',
  description: '電子部品（コネクタ）',
};

test('03-shipper-edit（荷主情報の訂正）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers');
  await page.getByRole('link', { name: '詳細' }).first().click();
  await page.getByRole('link', { name: '編集' }).click();
  await expect(page.getByRole('heading', { name: '荷主情報の訂正' })).toBeVisible();
  await capture(page, '03-shipper-edit.png');
});

test('04-booking-list（貨物予約一覧）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/bookings');
  // 空状態ではなく、代表的なデータが表示された状態で撮る
  await expect(page.getByText(DEMO_BOOKING.destination).first()).toBeVisible();
  await capture(page, '04-booking-list.png');
});

test('04-booking-form（貨物予約登録）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/bookings/new');
  await expect(page.getByRole('heading', { name: '貨物予約登録' })).toBeVisible();
  await capture(page, '04-booking-form.png');
});

test('04-booking-form-hazardous（危険物の入力欄）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/bookings/new');
  // **種別を選んだ状態で撮る。** 入力欄は種別を選んで初めて現れる
  await page.selectOption('#cargoType', 'HAZARDOUS');
  await expect(page.getByLabel('危険物クラス')).toBeVisible();
  await capture(page, '04-booking-form-hazardous.png');
});

test('04-booking-detail（予約詳細）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/bookings');
  await page.getByRole('link', { name: '詳細' }).first().click();
  await expect(page.getByRole('heading', { name: '予約詳細' })).toBeVisible();
  await capture(page, '04-booking-detail.png');
});

/** シードされた経路設計者。ROLE_ROUTER の画面を撮るために使う。 */
const ROUTER = { username: 'router', password: 'password' };

/**
 * 指定した利用者でログインする（ダッシュボードの見出しを待つ）。
 * @param {import('@playwright/test').Page} page ページ
 * @param {{username: string, password: string}} user 利用者
 */
async function loginAs(page, user) {
  await login(page, user);
}

test('05-voyage-list（航路一覧）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/voyages');
  // 空状態ではなく、代表的なデータが表示された状態で撮る
  await expect(page.getByText('さくら丸')).toBeVisible();
  await capture(page, '05-voyage-list.png');
});

test('05-voyage-form（航海スケジュール登録）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/voyages/new');
  await expect(page.getByRole('heading', { name: '航海スケジュール登録' })).toBeVisible();
  await capture(page, '05-voyage-form.png');
});

test('05-voyage-confirm（航海スケジュール更新の確認）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/voyages');
  await page.getByRole('link', { name: 'V0001' }).first().click();
  await page.getByRole('link', { name: 'スケジュールを更新する' }).click();
  // 差分が 1 件出る状態にしてから確認画面へ進む
  await page.fill('#vesselName', 'あさひ丸');
  await page.getByRole('button', { name: '変更内容を確認する' }).click();
  await expect(page.getByRole('heading', { name: /航海スケジュール更新の確認/ }))
    .toBeVisible();
  await capture(page, '05-voyage-confirm.png');
});

test('10-shipper-bookings（荷主が見る自社の予約）', async ({ page }) => {
  await login(page, { username: 'shipper', password: 'password' });
  await page.goto('/bookings');
  await expect(page.getByRole('heading', { name: /貨物予約/ })).toBeVisible();
  await capture(page, '10-shipper-bookings.png');
});

test('05-routing-queue（経路割り当て待ち）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/routing/queue');
  await expect(page.getByRole('heading', { name: '経路割り当て待ち' })).toBeVisible();
  await capture(page, '05-routing-queue.png');
});

test('04-booking-detail-assign（引き渡しボタン）', async ({ page }) => {
  await loginAs(page, SALES);
  await page.goto('/bookings');
  await page.getByRole('link', { name: '詳細' }).first().click();
  await expect(page.getByRole('heading', { name: '予約詳細' })).toBeVisible();
  await capture(page, '04-booking-detail-assign.png');
});

test('05-voyage-detail（航海詳細）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/voyages');
  // 乗り継ぎ便を開く。直行便では寄港地の行が写らない
  await page.getByRole('link', { name: 'V0002' }).click();
  await expect(page.getByRole('heading', { name: /航海詳細/ })).toBeVisible();
  await capture(page, '05-voyage-detail.png');
});

test('05-route-assignment（経路割り当て）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/routing/queue');
  await page.getByRole('link', { name: '経路を割り当て' }).first().click();
  await expect(page.getByRole('heading', { name: /経路割り当て/ })).toBeVisible();
  // 算出前ではなく、候補が並んだ状態で撮る
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  await expect(page.getByText('費用（概算）')).toBeVisible();
  await capture(page, '05-route-assignment.png');
});

const ADMIN = { username: 'admin', password: 'password' };

test('05-route-confirm（経路の確定）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/routing/queue');
  await page.getByRole('link', { name: '経路を割り当て' }).first().click();
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  await expect(page.getByRole('button', { name: 'この経路で確定' }).first()).toBeVisible();
  await capture(page, '05-route-confirm.png');
});

test('04-booking-itinerary（確定した経路）', async ({ page }) => {
  await loginAs(page, ROUTER);
  await page.goto('/routing/queue');
  await page.getByRole('link', { name: '経路を割り当て' }).first().click();
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  // 確認ダイアログを自動で承認する（キャプチャを撮るための操作）
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();
  await expect(page.getByRole('heading', { name: '予約詳細' })).toBeVisible();
  await capture(page, '04-booking-itinerary.png');
});

test('06-admin-accounts（ロック中アカウント）', async ({ page }) => {
  await loginAs(page, ADMIN);
  await page.goto('/admin/accounts');
  await expect(page.getByRole('heading', { name: 'ロック中のアカウント' })).toBeVisible();
  await capture(page, '06-admin-accounts.png');
});

/** シードされた追跡管理者・荷役作業員（IT6 で追加した章のキャプチャに使う）。 */
const TRACKER = { username: 'tracker', password: 'password' };
const HANDLER = { username: 'handler', password: 'password' };

/**
 * 予約を 1 件登録し、経路設計者に引き渡す.
 *
 * **一覧の先頭にある予約を使い回さない。** キャプチャは順に実行され、
 * 先に実行された節が待ち行列の予約を消費する。使い回すと、
 * 後ろの節だけが「対象が無い」で落ちる。
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<string>} 予約詳細の URL
 */
async function newBookingAwaitingRouting(page) {
  await loginAs(page, SALES);
  await page.goto('/bookings/new');
  await page.fill('#shipperCode', DEMO_SHIPPER.code);
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '1000');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'USLAX');
  await page.fill('#arrivalDeadline', localDate(60));
  await page.getByRole('button', { name: '登録する' }).click();
  const detailUrl = page.url();
  await page.getByRole('button', { name: '経路設計者に引き渡す' }).click();
  return detailUrl;
}

/**
 * 経路を確定して予約を確定し、追跡番号の発行を待つ状態まで進める.
 *
 * **キャプチャのために業務の順序をなぞる。** 途中の状態を DB に直接作ると、
 * 画面が実際に到達しうる状態かどうかを確かめないまま図を作ることになる。
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<string>} 予約詳細の URL
 */
async function confirmedBooking(page) {
  const bookingUrl = await newBookingAwaitingRouting(page);
  await loginAs(page, ROUTER);
  await page.goto(bookingUrl + '/route');
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();
  await expect(page.getByRole('heading', { name: '予約詳細' })).toBeVisible();

  await loginAs(page, SALES);
  await page.goto(bookingUrl);
  await page.getByRole('button', { name: '予約を確定' }).click();
  await expect(page.locator('.badge', { hasText: '確認済' }).first()).toBeVisible();
  return bookingUrl;
}

test('04-booking-detail-confirm（予約の確定）', async ({ page }) => {
  const detailUrl = await newBookingAwaitingRouting(page);
  await loginAs(page, ROUTER);
  await page.goto(detailUrl + '/route');
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();

  await loginAs(page, SALES);
  await page.goto(detailUrl);
  // **確定ボタンが出ている状態で撮る。** 押した後の画面ではボタンが消える
  await expect(page.getByRole('button', { name: '予約を確定' })).toBeVisible();
  await capture(page, '04-booking-detail-confirm.png');
});

test('07-tracking-queue（追跡番号発行待ち）', async ({ page }) => {
  await confirmedBooking(page);
  await loginAs(page, TRACKER);
  await page.goto('/tracking/queue');
  await expect(page.getByRole('heading', { name: '追跡番号発行待ち' })).toBeVisible();
  await capture(page, '07-tracking-queue.png');
});

test('07-tracking-issue（追跡番号の発行）', async ({ page }) => {
  const detailUrl = await confirmedBooking(page);
  await loginAs(page, TRACKER);
  await page.goto(detailUrl);
  await expect(page.getByRole('button', { name: '追跡番号を発行' })).toBeVisible();
  await capture(page, '07-tracking-issue.png');
});

test('08-handling-form（荷役作業登録）', async ({ page }) => {
  await loginAs(page, HANDLER);
  await page.goto('/handling/new');
  await expect(page.getByRole('heading', { name: '荷役作業登録' })).toBeVisible();
  await capture(page, '08-handling-form.png');
});

test('08-handling-list（荷役作業一覧）', async ({ page }) => {
  // 一覧は「記録がある状態」で撮る。空の一覧を代表の図に置くと、
  // 読者は自分の画面と一致しない図を見ることになる
  const detailUrl = await confirmedBooking(page);
  await loginAs(page, TRACKER);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();

  await loginAs(page, HANDLER);
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'RECEIVE');
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', 'JPOSA');
  await page.getByRole('button', { name: '登録する' }).click();
  await expect(page.getByRole('heading', { name: '荷役作業一覧' })).toBeVisible();
  await capture(page, '08-handling-list.png');
});

/**
 * 追跡番号を発行し、通関の荷役まで記録した貨物を用意する（US29）.
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<string>} 追跡番号
 */
async function customsReadyCargo(page) {
  const detailUrl = await confirmedBooking(page);
  await loginAs(page, TRACKER);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();

  await loginAs(page, HANDLER);
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'CUSTOMS');
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', 'USLAX');
  await page.getByRole('button', { name: '登録する' }).click();
  return trackingNumber;
}

test('08-handling-confirm（予定ルート外の作業の確認）', async ({ page }) => {
  const detailUrl = await confirmedBooking(page);
  await loginAs(page, TRACKER);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();
  const voyageNumber = await page.locator('code', { hasText: /^V/ }).first().innerText();

  await loginAs(page, HANDLER);
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'LOAD');
  await page.fill('#completionTime', localDateTime());
  // 旅程に無い港（承認画面を出すため）
  await page.fill('#locationUnlocode', 'JPYOK');
  await page.fill('#voyageNumber', voyageNumber);
  await page.getByRole('button', { name: '登録する' }).click();
  await expect(page.getByRole('heading', { name: '予定ルート外の作業です' })).toBeVisible();
  await capture(page, '08-handling-confirm.png');
});

test('08-customs-form（通関申告の登録）', async ({ page }) => {
  await loginAs(page, HANDLER);
  await page.goto('/handling/customs/new');
  await expect(page.getByRole('heading', { name: '通関申告の登録' })).toBeVisible();
  await capture(page, '08-customs-form.png');
});

test('08-customs-list（通関申告一覧）', async ({ page }) => {
  // 一覧は「記録がある状態」で撮る。空の一覧を代表の図に置かない
  const trackingNumber = await customsReadyCargo(page);
  await page.goto('/handling/customs/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.fill('#declarationNumber', `DEC-${Date.now()}`);
  await page.fill('#declaredAt', localDateTime());
  await page.getByRole('button', { name: '申告を登録する' }).click();
  await expect(page.getByRole('heading', { name: '通関管理' })).toBeVisible();
  await capture(page, '08-customs-list.png');
});

test('08-customs-detail（通関申告の詳細）', async ({ page }) => {
  // **履歴が 1 件も無い状態で撮らない。** 変更履歴はこの画面の要である
  const trackingNumber = await customsReadyCargo(page);
  const declarationNumber = `DEC-${Date.now()}`;
  await page.goto('/handling/customs/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.fill('#declarationNumber', declarationNumber);
  await page.fill('#declaredAt', localDateTime());
  await page.getByRole('button', { name: '申告を登録する' }).click();

  await page.getByRole('link', { name: declarationNumber }).click();
  await page.selectOption('#status', 'HELD');
  await page.fill('#reason', '書類の不備で保留されています');
  await page.getByRole('button', { name: '状態を更新する' }).click();
  await expect(page.getByRole('heading', { name: '通関申告の詳細' })).toBeVisible();
  await capture(page, '08-customs-detail.png');
});

/**
 * 引取まで済んだ貨物を用意する（訂正・取り消しのキャプチャに使う）.
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<string>} 追跡番号
 */
async function claimedCargo(page) {
  const detailUrl = await confirmedBooking(page);
  await loginAs(page, SALES);
  await page.goto(detailUrl);
  // **引取確認コードは予約詳細から読む**（US35）。任意の値では引き取れない
  const claimCode = await page.locator('code', { hasText: /^CLM-/ }).first().innerText();
  await page.fill('#consigneeName', '受取花子');
  await page.getByRole('button', { name: /荷受人を(登録|訂正)/ }).click();

  await loginAs(page, TRACKER);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();
  const voyageNumber = await page.locator('table code').first().innerText();

  await loginAs(page, HANDLER);
  for (const work of [
    { type: 'RECEIVE', location: 'JPOSA' },
    { type: 'LOAD', location: 'JPOSA', voyageNumber },
    { type: 'UNLOAD', location: 'USLAX', voyageNumber },
    { type: 'CUSTOMS', location: 'USLAX' },
  ]) {
    await page.goto('/handling/new');
    await page.fill('#trackingNumber', trackingNumber);
    await page.selectOption('#type', work.type);
    await page.fill('#completionTime', localDateTime());
    await page.fill('#locationUnlocode', work.location);
    if (work.voyageNumber) {
      await page.fill('#voyageNumber', work.voyageNumber);
    }
    await page.getByRole('button', { name: '登録する' }).click();
  }

  // **国をまたぐ輸送には通関が要る**（US29）。通さないと引取が拒まれる
  const declarationNumber = `DEC-MAN-${Date.now()}`;
  await page.goto('/handling/customs/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.fill('#declarationNumber', declarationNumber);
  await page.fill('#declaredAt', localDateTime());
  await page.getByRole('button', { name: '申告を登録する' }).click();
  await page.getByRole('link', { name: declarationNumber }).click();
  await page.selectOption('#status', 'CLEARED');
  await page.fill('#reason', '通関が完了しました');
  await page.getByRole('button', { name: '状態を更新する' }).click();

  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'CLAIM');
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', 'USLAX');
  await page.fill('#confirmationCode', claimCode);
  await page.fill('#consigneeName', '受取花子');
  await page.getByRole('button', { name: '登録する' }).click();
  return trackingNumber;
}

test('08-correction-form（訂正・取り消しの申請）', async ({ page }) => {
  const trackingNumber = await claimedCargo(page);
  await page.getByRole('row', { name: new RegExp(trackingNumber) })
    .getByRole('link', { name: '申請する' }).click();
  await expect(page.getByRole('heading', { name: '引取記録の訂正・取り消しを申請する' }))
    .toBeVisible();
  await capture(page, '08-correction-form.png');
});

test('07-corrections（訂正・取り消しの承認）', async ({ page }) => {
  // **承認待ちが 1 件も無い状態で撮らない。** この画面は待ち行列である
  const trackingNumber = await claimedCargo(page);
  await page.getByRole('row', { name: new RegExp(trackingNumber) })
    .getByRole('link', { name: '申請する' }).click();
  await page.selectOption('#type', 'CANCEL');
  await page.fill('#reason', '別の貨物と取り違えて登録した');
  await page.getByRole('button', { name: '申請する' }).click();

  await loginAs(page, TRACKER);
  await page.goto('/handling/corrections');
  await expect(page.getByRole('heading', { name: '訂正・取り消しの承認' })).toBeVisible();
  await capture(page, '07-corrections.png');
});

// ---------------------------------------------------------------------------
// 09. 貨物追跡（US18 / IT7）
// ---------------------------------------------------------------------------

const SHIPPER = { username: 'shipper', password: 'password' };

/**
 * 追跡番号を発行し、受領まで記録した貨物を用意する.
 *
 * **履歴が 1 件も無い状態で撮らない。** 空の履歴を代表の図に置くと、
 * 読者は自分の画面と一致しない図を見ることになる。
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<string>} 追跡番号
 */
async function trackedCargo(page) {
  const detailUrl = await confirmedBooking(page);
  await loginAs(page, TRACKER);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();

  await loginAs(page, HANDLER);
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'RECEIVE');
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', 'JPOSA');
  await page.getByRole('button', { name: '登録する' }).click();
  await expect(page.getByRole('heading', { name: '荷役作業一覧' })).toBeVisible();
  return trackingNumber;
}

test('05-route-relaxation（探索条件の緩和）', async ({ page }) => {
  const bookingUrl = await newBookingAwaitingRouting(page);
  await loginAs(page, ROUTER);
  await page.goto(bookingUrl + '/route');
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  // **緩めた後の状態で撮る。** 「当初から何日延ばしたか」は緩めて初めて出る
  await page.getByRole('button', { name: '+7 日' }).click();
  await expect(page.getByRole('heading', { name: '探索条件' })).toBeVisible();
  await capture(page, '05-route-relaxation.png');
});

test('04-booking-notification（荷主への経路通知）', async ({ page }) => {
  const bookingUrl = await newBookingAwaitingRouting(page);
  await loginAs(page, ROUTER);
  await page.goto(bookingUrl + '/route');
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();

  await loginAs(page, SALES);
  await page.goto(bookingUrl + '/notifications/new');
  await expect(page.getByRole('heading', { name: '荷主への経路通知' })).toBeVisible();
  await capture(page, '04-booking-notification.png');
});

test('04-booking-notification-history（通知履歴）', async ({ page }) => {
  const bookingUrl = await newBookingAwaitingRouting(page);
  await loginAs(page, ROUTER);
  await page.goto(bookingUrl + '/route');
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();

  await loginAs(page, SALES);
  await page.goto(bookingUrl + '/notifications/new');
  await page.getByRole('button', { name: 'この内容で通知する' }).click();
  // **送った後で撮る。** 空の履歴を代表の図に置くと、読者は自分の画面と一致しない図を見る
  await expect(page.getByRole('heading', { name: '通知履歴' })).toBeVisible();
  await capture(page, '04-booking-notification-history.png');
});

test('07-tracking-in-transit（追跡中の貨物）', async ({ page }) => {
  await trackedCargo(page);
  await loginAs(page, TRACKER);
  await page.goto('/tracking/queue');
  await expect(page.getByRole('heading', { name: '追跡中の貨物' })).toBeVisible();
  await capture(page, '07-tracking-in-transit.png');
});

test('07-tracking-manual-update（貨物状態の手動更新）', async ({ page }) => {
  const trackingNumber = await trackedCargo(page);
  await loginAs(page, TRACKER);
  await page.goto(`/tracking/${trackingNumber}`);
  await expect(page.getByRole('heading', { name: '状態を手動で更新' })).toBeVisible();
  await capture(page, '07-tracking-manual-update.png');
});

/**
 * 例外を起票した貨物を用意する（US19 / US20）.
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} type 例外種別（DELAY / DAMAGE / LOST）
 * @returns {Promise<string>} 追跡番号
 */
async function cargoWithException(page, type) {
  const trackingNumber = await trackedCargo(page);
  await loginAs(page, TRACKER);
  await page.goto(`/tracking/exceptions/new?trackingNumber=${trackingNumber}`);
  await page.selectOption('#exceptionType', type);
  await page.fill('#location', 'JPOSA');
  await page.fill('#occurredAt', localDateTime());
  await page.fill('#description', '台風により出港が 3 日遅れています');
  await page.getByRole('button', { name: '例外を登録' }).click();
  await expect(page.getByRole('heading', { name: '例外管理' })).toBeVisible();
  return trackingNumber;
}

test('07-tracking-exceptions（例外イベント一覧）', async ({ page }) => {
  await cargoWithException(page, 'DELAY');
  await capture(page, '07-tracking-exceptions.png');
});

test('07-tracking-exception-new（例外の登録）', async ({ page }) => {
  const trackingNumber = await trackedCargo(page);
  await loginAs(page, TRACKER);
  await page.goto(`/tracking/exceptions/new?trackingNumber=${trackingNumber}`);
  await expect(page.getByRole('heading', { name: '例外の登録' })).toBeVisible();
  await capture(page, '07-tracking-exception-new.png');
});

test('07-tracking-exception-detail（例外の詳細と対応）', async ({ page }) => {
  const trackingNumber = await cargoWithException(page, 'DELAY');
  await page.getByRole('row', { name: new RegExp(trackingNumber) })
    .getByRole('link', { name: '対応する' }).click();
  await expect(page.getByRole('heading', { name: '例外の詳細' })).toBeVisible();
  await capture(page, '07-tracking-exception-detail.png');
});

test('07-tracking-escalated（エスカレーション中の例外）', async ({ page }) => {
  await cargoWithException(page, 'LOST');
  await loginAs(page, ADMIN);
  await page.goto('/tracking/exceptions/escalated');
  await expect(page.getByRole('heading', { name: 'エスカレーション中の例外' })).toBeVisible();
  await capture(page, '07-tracking-escalated.png');
});

test('09-tracking-input（貨物追跡の入力）', async ({ page }) => {
  await loginAs(page, SHIPPER);
  await page.goto('/tracking');
  await expect(page.getByRole('heading', { name: '貨物追跡' })).toBeVisible();
  await capture(page, '09-tracking-input.png');
});

test('09-tracking-detail（追跡詳細）', async ({ page }) => {
  const trackingNumber = await trackedCargo(page);

  await loginAs(page, SHIPPER);
  await page.goto('/tracking');
  await page.fill('#trackingNumber', trackingNumber);
  await page.getByRole('button', { name: '追跡する' }).click();
  await expect(page.getByRole('heading', { name: '追跡詳細' })).toBeVisible();
  await capture(page, '09-tracking-detail.png');
});

test('09-public-tracking（公開追跡）', async ({ page }) => {
  const trackingNumber = await trackedCargo(page);

  // **ログアウトしてから撮る。** ログイン済みのまま撮ると、
  // 未ログインの読者が見る画面と食い違う
  await page.goto('/login');
  if (!page.url().includes('/login')) {
    await page.getByRole('button', { name: 'ログアウト' }).click();
  }
  await page.context().clearCookies();

  await page.goto(`/public/tracking?trackingNumber=${trackingNumber}`);
  await expect(page.getByRole('heading', { name: 'CargoTracker 公開追跡' })).toBeVisible();
  await capture(page, '09-public-tracking.png');
});
