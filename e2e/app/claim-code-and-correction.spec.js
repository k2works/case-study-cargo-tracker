import { test, expect } from '@playwright/test';
import { localDate, localDateTime } from './support/time.js';

/**
 * 引取確認コードの照合（US35）と、引取記録の取り消し（US36）。
 *
 * IT7 の引取記録は**提示された値をそのまま書き写すだけ**で、照合する相手が
 * システムの中に無かった。**記録はできるが引き渡しの証明にならない**。
 *
 * 引取は輸送の終点であり、**誤登録をそのままにすると貨物が届いていないのに
 * 配送完了として扱われる**。取り消しには追跡管理者の承認が要る。
 *
 * ここで確かめるのは「操作できたこと」ではない。**誤ったコードでは引き取れないこと**と、
 * **承認して初めて状態が戻ること**を、画面を通してつなぐ。
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
 * 荷降しまで進んだ貨物を用意する.
 * @param {import('@playwright/test').Page} page ページ
 * @returns {Promise<{trackingNumber: string, detailUrl: string, claimCode: string}>} 貨物
 */
async function 荷降し済みの貨物を用意する(page) {
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
  await page.getByRole('button', { name: '経路候補を算出する' }).click();
  await expect(page.getByRole('button', { name: 'この経路で確定' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);

  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '予約を確定' }).click();

  // **確定と採番はひと組である**（US35）。ここで初めてコードが読める
  const claimCode = await page.locator('code', { hasText: /^CLM-/ }).first().innerText();

  await loginAs(page, USERS.tracker);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();
  // **確定した便で積み込む。** 決め打ちの番号で積むとそれは誤配であり、
  // 意図した経路を通らないまま「輸送中」だけが緑になる
  const voyageNumber = await page.locator('table code').first().innerText();

  await loginAs(page, USERS.handler);
  await 荷役を登録する(page, trackingNumber, 'RECEIVE', 'JPOSA');
  // **積込で輸送が始まる**（遷移表 #6）。これが無いと予約は追跡番号発行済のままで、
  // 引取を登録しても配送完了にならない
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'LOAD');
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', 'JPOSA');
  await page.fill('#voyageNumber', voyageNumber);
  await page.getByRole('button', { name: '登録する' }).click();
  await 荷役を登録する(page, trackingNumber, 'UNLOAD', 'USLAX');

  // **引取までに荷受人を入れる**（US16）。入れないと引取で警告が出る
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.fill('#consigneeName', '受取花子');
  await page.getByRole('button', { name: /荷受人を(登録|訂正)/ }).click();

  await loginAs(page, USERS.handler);
  // **国をまたぐ輸送には通関が要る**（US29 / C29）。通関を通しておかないと、
  // 引取はコードの照合まで届かず「通関が未登録」で止まる。
  // **止めているのが何かを判別できる形にする**
  await 荷役を登録する(page, trackingNumber, 'CUSTOMS', 'USLAX');
  const declarationNumber = `DEC-CLM-${Date.now()}`;
  await page.goto('/handling/customs/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.fill('#declarationNumber', declarationNumber);
  await page.fill('#declaredAt', localDateTime());
  await page.getByRole('button', { name: '申告を登録する' }).click();
  await page.getByRole('link', { name: declarationNumber }).click();
  await page.selectOption('#status', 'CLEARED');
  await page.fill('#reason', '通関が完了しました');
  await page.getByRole('button', { name: '状態を更新する' }).click();

  return { trackingNumber, detailUrl, claimCode };
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
  if (type === 'CLAIM') {
    await page.fill('#confirmationCode', confirmationCode);
    await page.fill('#consigneeName', '受取花子');
  }
  await page.getByRole('button', { name: '登録する' }).click();
}

test('採番したコードでだけ引き取れ、誤登録は承認を経て取り消せる', async ({ page }) => {
  // **確認ダイアログを承諾する。** 受け取らないと画面が固まり、
  // 「遷移しない」という別の失敗に見える
  page.on('dialog', (dialog) => dialog.accept());

  const { trackingNumber, detailUrl, claimCode } = await 荷降し済みの貨物を用意する(page);

  // ---- 追跡番号を入れても引き取れない（US35。**別の値である**）----
  await 荷役を登録する(page, trackingNumber, 'CLAIM', 'USLAX', trackingNumber);
  await expect(page.locator('.alert-danger')).toContainText('引取確認コードが一致しません');

  // ---- 当てずっぽうのコードでも引き取れない ----
  await 荷役を登録する(page, trackingNumber, 'CLAIM', 'USLAX', 'CLM-99999999');
  await expect(page.locator('.alert-danger')).toContainText('引取確認コードが一致しません');

  // ---- 採番されたコードなら引き取れる。**通ることも確かめる** ----
  await 荷役を登録する(page, trackingNumber, 'CLAIM', 'USLAX', claimCode);
  await expect(page.locator('.alert-success')).toContainText('引取');

  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expect(page.locator('.badge', { hasText: '配送完了' }).first()).toBeVisible();

  // ---- 誤登録に気づいて取り消しを申請する（US36）----
  await loginAs(page, USERS.handler);
  await page.getByRole('link', { name: '荷役管理' }).click();
  await page.getByRole('row', { name: new RegExp(trackingNumber) })
    .getByRole('link', { name: '申請する' }).click();
  await page.selectOption('#type', 'CANCEL');
  await page.fill('#reason', '別の貨物と取り違えて登録した');
  await page.getByRole('button', { name: '申請する' }).click();

  // **申請しただけでは状態は戻らない。** 承認という段階に意味がある
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expect(page.locator('.badge', { hasText: '配送完了' }).first()).toBeVisible();

  // ---- 追跡管理者が承認する ----
  await loginAs(page, USERS.tracker);
  await page.getByRole('link', { name: '訂正・取り消し' }).click();
  await expect(page.getByRole('cell', { name: '別の貨物と取り違えて登録した' })).toBeVisible();
  await page.getByRole('button', { name: '承認' }).first().click();

  // ---- 貨物の状態が引取前に戻る ----
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expect(page.locator('.badge', { hasText: '輸送中' }).first()).toBeVisible();

  // **元の記録は消えない。** 誰がいつ登録したかが読めなくなると経緯を追えない
  await loginAs(page, USERS.handler);
  await page.getByRole('link', { name: '荷役管理' }).click();
  await expect(page.getByRole('row', { name: new RegExp(trackingNumber) }).first())
    .toContainText('取り消し済み');
});
