import { test, expect } from '@playwright/test';
import { localDate, localDateTime } from './support/time.js';

/**
 * 通関を通してから引き渡す（US29）。
 *
 * 通関は業務上の**唯一の「止まる仕組み」**である。誤配も荷受人違いも「起きた事実」
 * として記録するが、**通関前の引き渡しは実行してはならない**。
 *
 * ここで確かめるのは「申告を登録できたこと」ではない。**止まること**（引取が拒まれる）と
 * **通ること**（通関済にすると引取できる）を、画面を通してつなぐ。
 */

const USERS = {
  sales: { username: 'sales', password: 'password' },
  router: { username: 'router', password: 'password' },
  tracker: { username: 'tracker', password: 'password' },
  handler: { username: 'handler', password: 'password' },
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
 * 追跡番号まで発行済みの貨物を用意する.
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<{trackingNumber: string, detailUrl: string}>} 追跡番号と予約詳細の URL
 */
async function 追跡中の貨物を用意する(page) {
  await loginAs(page, USERS.sales);
  await page.goto('/bookings/new');
  await page.fill('#shipperCode', 'SHP-000001');
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '700');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'USLAX');
  await page.fill('#arrivalDeadline', localDate(45));
  await page.getByRole('button', { name: '登録する' }).click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);
  const detailUrl = page.url();

  await page.getByRole('button', { name: '経路設計者に引き渡す' }).click();

  await loginAs(page, USERS.router);
  await page.goto(`${detailUrl}/route`);
  await page.getByRole('button', { name: '経路候補を算出する' }).click();
  await expect(page.getByRole('button', { name: 'この経路で確定' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);

  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '予約を確定' }).click();

  await loginAs(page, USERS.tracker);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();

  return { trackingNumber, detailUrl };
}

/**
 * 荷役を 1 件登録する.
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} trackingNumber 追跡番号
 * @param {string} type 荷役種別
 * @param {string} location 作業場所
 */
async function 荷役を登録する(page, trackingNumber, type, location) {
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', type);
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', location);
  // 引取は荷受人確認を伴う（US16）。**種別ごとの必須項目は種別が知っている**
  if (type === 'CLAIM') {
    await page.fill('#confirmationCode', '123456');
    await page.fill('#consigneeName', '受取花子');
  }
  await page.getByRole('button', { name: '登録する' }).click();
}

test('通関が下りるまで引取は登録できない', async ({ page }) => {
  // **確認ダイアログを承諾する。** 受け取らないと画面が固まり、
  // 「遷移しない」という別の失敗に見える
  page.on('dialog', (dialog) => dialog.accept());

  const { trackingNumber } = await 追跡中の貨物を用意する(page);
  const declarationNumber = `DEC-E2E-${Date.now()}`;

  // ---- 荷役作業員: 受領から荷降しまで進める ----
  await loginAs(page, USERS.handler);
  await 荷役を登録する(page, trackingNumber, 'RECEIVE', 'JPOSA');
  await 荷役を登録する(page, trackingNumber, 'CUSTOMS', 'USLAX');

  // ---- 通関申告を登録する（審査中で始まる） ----
  await page.getByRole('link', { name: '通関管理' }).click();
  await page.getByRole('link', { name: '新規申告' }).click();
  await page.fill('#trackingNumber', trackingNumber);
  await page.fill('#declarationNumber', declarationNumber);
  await page.fill('#declaredAt', localDateTime());
  await page.getByRole('button', { name: '申告を登録する' }).click();

  await expect(page.locator('.alert-success')).toContainText('審査中');
  await expect(page.getByRole('cell', { name: trackingNumber })).toBeVisible();

  // ---- 引取は拒まれる。**なぜ止まっているかが読める** ----
  await 荷役を登録する(page, trackingNumber, 'CLAIM', 'USLAX');
  await expect(page.locator('.alert-danger')).toContainText('通関が完了していない');
  await expect(page.locator('.alert-danger')).toContainText('審査中');

  // ---- 留置にすると、追跡側に税関保留の例外が現れる ----
  await page.goto('/handling/customs');
  await page.getByRole('link', { name: declarationNumber }).click();
  await page.selectOption('#status', 'HELD');
  await page.fill('#reason', '書類の不備で保留されています');
  await page.getByRole('button', { name: '状態を更新する' }).click();

  // **変更履歴が読める。** なぜ止めたのかが残らないと、後から誰も検証できない
  await expect(page.getByText('変更履歴')).toBeVisible();
  await expect(page.getByRole('cell', { name: '書類の不備で保留されています' })).toBeVisible();

  await loginAs(page, USERS.tracker);
  await page.getByRole('link', { name: '例外管理' }).click();
  await expect(page.getByRole('row', { name: new RegExp(trackingNumber) }))
    .toContainText('税関保留');

  // ---- 通関済にすると引取できる。**通ることも確かめる** ----
  await loginAs(page, USERS.handler);
  await page.goto('/handling/customs');
  await page.getByRole('link', { name: declarationNumber }).click();
  await page.selectOption('#status', 'CLEARED');
  await page.fill('#reason', '不備が解消しました');
  await page.getByRole('button', { name: '状態を更新する' }).click();
  await expect(page.locator('.alert-success')).toContainText('通関済');

  await 荷役を登録する(page, trackingNumber, 'CLAIM', 'USLAX');
  await expect(page.locator('.alert-success')).toContainText('引取');

  // ---- 荷主: 通関完了が通知の記録として残り、本文まで読める（C20） ----
  await loginAs(page, USERS.sales);
  await page.goto((await 追跡中の貨物の予約詳細(page, trackingNumber)) ?? '/bookings');
  await expect(page.getByRole('cell', { name: '通関完了' }).first()).toBeVisible();
});

/**
 * 追跡番号から予約詳細の URL を引く.
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} trackingNumber 追跡番号
 * @returns {Promise<string|null>} 予約詳細の URL
 */
async function 追跡中の貨物の予約詳細(page, trackingNumber) {
  await page.goto(`/bookings?trackingNumber=${trackingNumber}`);
  const link = page.getByRole('link', { name: '詳細' }).first();
  if ((await link.count()) === 0) {
    return null;
  }
  await link.click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);
  return page.url();
}
