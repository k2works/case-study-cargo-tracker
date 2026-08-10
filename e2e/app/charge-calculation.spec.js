import { test, expect } from '@playwright/test';
import { localDate, localDateTime } from './support/time.js';

/**
 * 輸送料金の算出と確定（US21 / US22）。
 *
 * v1.1.0 は予約から引取までを通したが、**その先が無かった**。引取が済んだ貨物は
 * 「配送完了」のまま止まり、経理担当者にはダッシュボードのカードすら無かった。
 * **会社は運んだが、請求できない。**
 *
 * ここで確かめるのは「操作できたこと」ではない。**引取から請求までが画面を通して
 * つながること**と、**確定した金額が動かないこと**を見る。
 */

const USERS = {
  sales: { username: 'sales', password: 'password' },
  router: { username: 'router', password: 'password' },
  tracker: { username: 'tracker', password: 'password' },
  handler: { username: 'handler', password: 'password' },
  billing: { username: 'billing', password: 'password' },
};

/**
 * 指定した利用者でログインする（前の利用者はログアウトする）.
 * @param {import('@playwright/test').Page} page ページ
 * @param {{username: string, password: string}} user 利用者
 */
async function loginAs(page, user) {
  await page.goto('/login?logout');
  await page.fill('#username', user.username);
  await page.fill('#password', user.password);
  await page.getByRole('button', { name: 'ログイン' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

/**
 * 荷役を 1 件登録する.
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} trackingNumber 追跡番号
 * @param {string} type 荷役種別
 * @param {string} location 作業場所
 * @param {string} [confirmationCode] 引取確認コード（引取のときだけ使う）
 */
async function 荷役を登録する(page, trackingNumber, type, location, confirmationCode) {
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', type);
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', location);
  if (confirmationCode) {
    await page.fill('#confirmationCode', confirmationCode);
    await page.fill('#consigneeName', '受取花子');
  }
  await page.getByRole('button', { name: '登録する' }).click();
}

/**
 * 引取まで済んだ貨物を用意する.
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<{trackingNumber: string, detailUrl: string}>} 貨物
 */
async function 引取済みの貨物を用意する(page) {
  // **確認ダイアログを受け入れる。** 既定では dismiss され、
  // confirm() を返すボタンは **POST そのものが飛ばない**。
  // 「押したのに何も起きない」形は、画面にもログにも痕跡が残らない
  page.on('dialog', (dialog) => dialog.accept());
  await loginAs(page, USERS.sales);
  await page.goto('/bookings/new');
  await page.fill('#shipperCode', 'SHP-000001');
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '600');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'USLAX');
  await page.fill('#arrivalDeadline', localDate(45));
  await page.getByRole('button', { name: '登録する' }).click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);
  const detailUrl = page.url();

  await page.getByRole('button', { name: '経路設計者に引き渡す' }).click();

  await loginAs(page, USERS.router);
  await page.goto(`${detailUrl}/route`);
  // **算出は PRG である。** 押した直後に画面が入れ替わるため、
  // 「見えたら押す」だけだとクリックが古い DOM に当たり、POST が飛ばない。
  // **待ちと操作を同時に張る** — 時間で待つと、遅い日にだけ落ちる
  await Promise.all([
    page.waitForURL(/\/bookings\/[0-9a-f-]+\/route$/),
    page.getByRole('button', { name: '経路候補を算出する' }).click(),
  ]);
  await expect(page.getByRole('button', { name: 'この経路で確定' }).first()).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/bookings\/[0-9a-f-]+$/),
    page.getByRole('button', { name: 'この経路で確定' }).first().click(),
  ]);

  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '予約を確定' }).click();
  const claimCode = await page.locator('code', { hasText: /^CLM-/ }).first().innerText();

  await loginAs(page, USERS.tracker);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();
  const voyageNumber = await page.locator('table code').first().innerText();

  await loginAs(page, USERS.handler);
  await 荷役を登録する(page, trackingNumber, 'RECEIVE', 'JPOSA');
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'LOAD');
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', 'JPOSA');
  await page.fill('#voyageNumber', voyageNumber);
  await page.getByRole('button', { name: '登録する' }).click();
  await 荷役を登録する(page, trackingNumber, 'UNLOAD', 'USLAX');

  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.fill('#consigneeName', '受取花子');
  await page.getByRole('button', { name: /荷受人を(登録|訂正)/ }).click();

  // **国をまたぐ輸送には通関が要る**（US29）。通さないと引取が止まる
  await loginAs(page, USERS.handler);
  await 荷役を登録する(page, trackingNumber, 'CUSTOMS', 'USLAX');
  const declarationNumber = `DEC-BIL-${Date.now()}`;
  await page.goto('/handling/customs/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.fill('#declarationNumber', declarationNumber);
  await page.fill('#declaredAt', localDateTime());
  await page.getByRole('button', { name: '申告を登録する' }).click();
  await page.getByRole('link', { name: declarationNumber }).click();
  await page.selectOption('#status', 'CLEARED');
  await page.fill('#reason', '通関が完了しました');
  await page.getByRole('button', { name: '状態を更新する' }).click();

  // **引取で配送完了になる。** ここまで来て初めて請求できる
  await 荷役を登録する(page, trackingNumber, 'CLAIM', 'USLAX', claimCode);

  return { trackingNumber, detailUrl };
}

test.describe('輸送料金の算出と確定（US21 / US22）', () => {
  test('引取済みの貨物を請求対象から算出し確定できる', async ({ page }) => {
    const { trackingNumber } = await 引取済みの貨物を用意する(page);

    await loginAs(page, USERS.billing);

    // **ダッシュボードから気づいて、そこから対象へ行ける**（IT9 の Try T2）
    await page.goto('/');
    await expect(page.getByRole('heading', { name: '未請求の引取済貨物' })).toBeVisible();

    await page.goto('/billing/pending');
    const row = page.locator('tr', { hasText: trackingNumber });
    await expect(row).toBeVisible();

    // 受入基準 2: 輸送実績が表示される
    await expect(row).toContainText('JPOSA → USLAX');

    // 受入基準 1・3: 引取済みの貨物から料金を算出できる（基本料金は自動計算）
    await row.getByRole('button', { name: '料金を算出' }).click();
    await page.waitForURL(/\/billing\/invoices\/INV-/);

    // 受入基準 4: 算出しただけでは確定していない
    await expect(page.getByText('下書き').first()).toBeVisible();
    // US22: 割引の根拠が並ぶ（個人荷主でも率 0% の行を出す）
    await expect(page.getByText('割引率')).toBeVisible();
    await expect(page.getByText('割引後料金')).toBeVisible();

    // 受入基準 5: 確定すると「確定」状態になる（確認ダイアログは準備で受け入れ済み）
    await page.getByRole('button', { name: '料金を確定' }).click();
    await expect(page.getByText('料金を確定しました')).toBeVisible();

    // **確定後は金額を動かす入口が消える。** 確定という操作に意味を持たせる
    await expect(page.getByRole('button', { name: '反映する' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '料金を確定' })).toHaveCount(0);

    // **二重請求の入口を画面に置かない**
    await page.goto('/billing/pending');
    await expect(page.locator('tr', { hasText: trackingNumber })).toHaveCount(0);
  });

  test('例外がある貨物では料金調整を入力できる', async ({ page }) => {
    const { trackingNumber } = await 引取済みの貨物を用意する(page);

    await loginAs(page, USERS.billing);
    await page.goto('/billing/pending');
    await page
      .locator('tr', { hasText: trackingNumber })
      .getByRole('button', { name: '料金を算出' })
      .click();
    await page.waitForURL(/\/billing\/invoices\/INV-/);

    const before = await page.locator('tbody tr').last().innerText();

    await page.fill('#reduction', '100');
    await page.fill('#compensation', '50');
    await page.fill('#reason', '遅延による減額と代替輸送費');
    await page.getByRole('button', { name: '反映する' }).click();

    await expect(page.getByText('料金調整を反映しました')).toBeVisible();
    await expect(page.getByText('遅延による減額と代替輸送費')).toBeVisible();
    // **金額が実際に動く。** 理由だけが残って金額が変わらない形にしない
    await expect(page.locator('tbody tr').last()).not.toHaveText(before);
  });

  test('確定した請求書を発行して入金を確認すると予約が精算済みになる', async ({ page }) => {
    const { trackingNumber, detailUrl } = await 引取済みの貨物を用意する(page);

    await loginAs(page, USERS.billing);
    await page.goto('/billing/pending');
    await page
      .locator('tr', { hasText: trackingNumber })
      .getByRole('button', { name: '料金を算出' })
      .click();
    await page.waitForURL(/\/billing\/invoices\/INV-/);
    const invoiceUrl = page.url();

    await page.getByRole('button', { name: '料金を確定' }).click();

    // 受入基準 1・2: 確定した料金をもとに精算書を発行し、支払期限が決まる
    await page.getByRole('button', { name: '精算書を発行' }).click();
    await expect(page.getByText('精算書を発行しました')).toBeVisible();
    await expect(page.getByText('支払期限')).toBeVisible();
    await expect(page.getByText('未入金').first()).toBeVisible();

    // **発行は一度だけ。** 同じ請求書を 2 通送ることになる
    await expect(page.getByRole('button', { name: '精算書を発行' })).toHaveCount(0);

    // 受入基準 3: 荷主へ伝えた記録が残る（ADR-006 により外部へは送らない）
    await loginAs(page, USERS.sales);
    await page.goto(detailUrl);
    await expect(page.getByText('精算書の発行')).toBeVisible();

    // 受入基準 4: 請求額どおりの入金を確認すると精算済みになる
    await loginAs(page, USERS.billing);
    await page.goto(invoiceUrl);
    const total = await page.inputValue('#paidAmount');
    await page.fill('#paidAmount', total);
    await page.getByRole('button', { name: '入金を確認' }).click();
    await expect(page.getByText('入金を確認しました')).toBeVisible();
    await expect(page.getByText('入金確認済').first()).toBeVisible();

    // **予約状態も精算済になる。** 請求書の中で完結すると、
    // 「精算済みには訂正・取り消しできない」（US36）が効き始めない
    await loginAs(page, USERS.sales);
    await page.goto(detailUrl);
    await expect(page.getByText('精算完了').first()).toBeVisible();
  });

  test('請求額と違う入金は受け付けない', async ({ page }) => {
    const { trackingNumber } = await 引取済みの貨物を用意する(page);

    await loginAs(page, USERS.billing);
    await page.goto('/billing/pending');
    await page
      .locator('tr', { hasText: trackingNumber })
      .getByRole('button', { name: '料金を算出' })
      .click();
    await page.waitForURL(/\/billing\/invoices\/INV-/);
    await page.getByRole('button', { name: '料金を確定' }).click();
    await page.getByRole('button', { name: '精算書を発行' }).click();

    // **一部入金は認めない**（ADR-018）。差額の扱いは業務である
    await page.fill('#paidAmount', '1');
    await page.getByRole('button', { name: '入金を確認' }).click();

    await expect(page.getByText(/入金額が請求額と一致しません/)).toBeVisible();
    await expect(page.getByText('未入金').first()).toBeVisible();
  });

  test('経理担当者以外は請求の画面を開けない', async ({ page }) => {
    await loginAs(page, USERS.sales);

    const response = await page.goto('/billing/pending');
    expect(response.status()).toBe(403);
  });
});
