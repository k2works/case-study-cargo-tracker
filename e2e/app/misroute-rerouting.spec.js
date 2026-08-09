import { test, expect } from '@playwright/test';
import { localDate, localDateTime } from './support/time.js';

/**
 * 誤配を検知して経路を組み直す（US28）。
 *
 * **誤配は記録して終わりではない。** 貨物は予定と違う港にあり、そこから目的地まで
 * 引き直さないと動かない。
 *
 * ここで確かめるのは、受入基準の順序である。**登録「前」に警告が出て、承認して初めて
 * 記録される**。登録したあとに「予定と違いました」と伝えるのでは、作業員は取り消す
 * 手段を持たない（取り消しは US36 でまだ無い）。
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
 * @returns {Promise<{trackingNumber: string, detailUrl: string, voyageNumber: string}>} 貨物
 */
async function 追跡中の貨物を用意する(page) {
  await loginAs(page, USERS.sales);
  await page.goto('/bookings/new');
  await page.fill('#shipperCode', 'SHP-000001');
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '800');
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
  const voyageNumber = await page.locator('code', { hasText: /^V/ }).first().innerText();

  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '予約を確定' }).click();

  await loginAs(page, USERS.tracker);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();

  return { trackingNumber, detailUrl, voyageNumber };
}

test('予定ルート外の積込は承認してから記録され、現在地から引き直せる', async ({ page }) => {
  // **確認ダイアログを承諾する。** 受け取らないと画面が固まり、
  // 「遷移しない」という別の失敗に見える
  page.on('dialog', (dialog) => dialog.accept());

  const { trackingNumber, detailUrl, voyageNumber } = await 追跡中の貨物を用意する(page);

  // ---- 荷役作業員: 予定に無い港で積込を送る ----
  await loginAs(page, USERS.handler);
  await page.goto('/handling/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'LOAD');
  await page.fill('#completionTime', localDateTime());
  // 旅程は JPOSA → USLAX。ここは予定に無い
  await page.fill('#locationUnlocode', 'JPYOK');
  await page.fill('#voyageNumber', voyageNumber);
  await page.getByRole('button', { name: '登録する' }).click();

  // ---- 登録「前」に警告が出る（受入基準の順序） ----
  await expect(page.getByRole('heading', { name: '予定ルート外の作業です' })).toBeVisible();
  await expect(page.getByText('予定ルートに無い')).toBeVisible();

  // **やめる側の導線がある。** 承認しか選べないなら、それは警告ではない
  await expect(page.getByRole('button', { name: '入力に戻る' })).toBeVisible();

  // ---- 承認して登録する ----
  await page.getByRole('button', { name: '承認して登録する' }).click();
  await expect(page.locator('.alert-success')).toContainText('積込');

  // ---- 追跡管理者: 誤配の例外が自動で起票されている ----
  await loginAs(page, USERS.tracker);
  await page.getByRole('link', { name: '例外管理' }).click();
  await expect(page.getByRole('row', { name: new RegExp(trackingNumber) }))
    .toContainText('誤配');

  // ---- 予約詳細に誤配のバナーと現在地が出る ----
  await page.goto(detailUrl);
  await expect(page.getByRole('heading', { name: '誤配が検知されています' })).toBeVisible();
  await expect(page.getByText('貨物の現在地は JPYOK です')).toBeVisible();

  // ---- 経路設計者: 現在地から引き直す ----
  await loginAs(page, USERS.router);
  await page.goto(detailUrl);
  await page.getByRole('link', { name: '経路を再設計' }).click();

  // **画面が言うとおりに探す。** 予約の出発地から引き直すと、
  // すでに動いた分をなかったことにした経路が出る
  await expect(page.getByText('誤配のため現在地 JPYOK から再設計しています')).toBeVisible();
  // **ボタンの文言は算出済みかどうかで変わる。** 誤配の予約は経路を確定済みであり、
  // 「再算出する」になる
  await page.getByRole('button', { name: /経路候補を(再)?算出する/ }).click();

  // 候補が出るかどうかは航路の登録次第である。**出た場合は現在地発であることを見る**
  const firstVoyage = page.getByRole('button', { name: 'この経路で確定' }).first();
  if ((await firstVoyage.count()) > 0) {
    // 探索の出発地が現在地になっている（当初の出発地も併記される）
    await expect(page.getByText('JPYOK → USLAX')).toBeVisible();
    await expect(page.getByText('（当初の出発地は JPOSA）')).toBeVisible();
  } else {
    // 候補ゼロも業務上ありうる。**その場合も画面は開けており、理由が読める**
    await expect(page.getByText('条件に合う経路が見つかりません')).toBeVisible();
  }
});
