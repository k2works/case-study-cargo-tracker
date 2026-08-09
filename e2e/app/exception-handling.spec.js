import { test, expect } from '@playwright/test';
import { localDate, localDateTime } from './support/time.js';

/**
 * 例外が起きてから片づくまで（US19 / US20）。
 *
 * これまでの E2E が確かめてきたのは「うまくいく道」だった。ここで確かめるのは
 * **その道から外れたとき**である。遅延・破損・紛失は例外的な出来事ではなく、
 * 国際輸送では日常的に起きる。
 *
 * **見るのは「記録できたこと」ではない。** 荷主に伝わったか（通知の記録）、
 * 片づいたときに正しい状態へ戻るか、管理者が気づけるか、までを画面でつなぐ。
 */

const USERS = {
  sales: { username: 'sales', password: 'password' },
  router: { username: 'router', password: 'password' },
  tracker: { username: 'tracker', password: 'password' },
  admin: { username: 'admin', password: 'password' },
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
  await page.fill('#weight', '900');
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

test('遅延を登録すると荷主に伝わり、対応すると発生前の状態に戻る', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  const { trackingNumber, detailUrl } = await 追跡中の貨物を用意する(page);

  // ---- 追跡管理者: 追跡詳細から例外を登録する ----
  // **発生時点の到達性。** 遅延に気づくのは、この画面で状況を見ているときである
  await loginAs(page, USERS.tracker);
  await page.goto(`/tracking/${trackingNumber}`);
  await page.getByRole('link', { name: '例外を登録', exact: true }).click();

  // **追跡番号は埋まっている。** 手で書き写させると、写し間違えた別の貨物に例外が付く
  await expect(page.locator('#trackingNumber')).toHaveValue(trackingNumber);

  await page.selectOption('#exceptionType', 'DELAY');
  await page.fill('#location', 'JPOSA');
  await page.fill('#occurredAt', localDateTime());
  await page.fill('#description', '台風により出港が 3 日遅れています');
  await page.getByRole('button', { name: '例外を登録' }).click();

  await expect(page.locator('.alert-success')).toContainText('例外を登録しました');
  // 一覧は「連絡すべき仕事の待ち行列」である。**誰に連絡するのかが読める**
  await expect(page.getByRole('cell', { name: trackingNumber })).toBeVisible();

  // ---- 貨物の状態が「例外」になっている ----
  await page.goto(`/tracking/${trackingNumber}`);
  await expect(page.getByText('例外', { exact: true }).first()).toBeVisible();

  // ---- 荷主: 発生の通知が記録されている（ADR-006 により外部へは送らない） ----
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expect(page.getByRole('cell', { name: '例外発生' }).first()).toBeVisible();

  // ---- 追跡管理者: 対応内容を入れて解決する ----
  await loginAs(page, USERS.tracker);
  await page.getByRole('link', { name: '例外管理' }).click();
  await page.getByRole('row', { name: new RegExp(trackingNumber) })
    .getByRole('link', { name: '対応する' }).click();

  // **押す前に、解決したらどこへ戻るのかが読める**
  await expect(page.getByText('解決後に戻る状態')).toBeVisible();

  await page.fill('#resolutionNotes', '代替便に振り替えました。到着予定は 3 日後です');
  await page.getByRole('button', { name: '対応を記録する' }).click();
  await expect(page.locator('.alert-success')).toContainText('対応報告を記録しました');

  // 片づいた例外は待ち行列から消える（残ると、いま何をすべきかが読めない）
  await expect(page.getByRole('cell', { name: trackingNumber })).toHaveCount(0);

  // ---- 発生前の状態に戻っている ----
  // **「例外」でも「未受取」でもない。** 発生前の状態を永続化しているから戻せる
  await page.goto(`/tracking/${trackingNumber}`);
  await expect(page.getByText('未受取').first()).toBeVisible();

  // ---- 荷主: 対応報告も記録されている ----
  // **発生と対応報告は別の知らせである。** 同じ種別だと履歴で区別できない
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expect(page.getByRole('cell', { name: '例外対応報告' }).first()).toBeVisible();
});

test('紛失はエスカレーションされ、管理者がダッシュボードから気づける', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  const { trackingNumber } = await 追跡中の貨物を用意する(page);

  // ---- 追跡管理者: 紛失を登録する ----
  await loginAs(page, USERS.tracker);
  await page.goto(`/tracking/exceptions/new?trackingNumber=${trackingNumber}`);
  await page.selectOption('#exceptionType', 'LOST');
  await page.fill('#location', 'JPOSA');
  await page.fill('#occurredAt', localDateTime());
  await page.fill('#description', '積み替え時に所在が分からなくなりました');
  await page.getByRole('button', { name: '例外を登録' }).click();

  await expect(page.getByRole('cell', { name: 'エスカレーション' }).first()).toBeVisible();

  // ---- 管理者: ダッシュボードのカードから気づける ----
  // **「送った」だけで誰も見ないなら意味が無い。** US20 の受入基準
  // 「管理職への escalation 通知」の受け皿はこの導線である
  await loginAs(page, USERS.admin);
  await expect(page.getByRole('heading', { name: 'エスカレーション中' })).toBeVisible();
  await page.getByRole('heading', { name: 'エスカレーション中' })
    .locator('..').getByRole('link', { name: '開く' }).click();

  await expect(page.getByRole('cell', { name: trackingNumber })).toBeVisible();
  // 対応は追跡管理者の仕事である。管理者は内容を見るだけ
  await page.getByRole('row', { name: new RegExp(trackingNumber) })
    .getByRole('link', { name: '内容を見る' }).click();

  // **「無いこと」だけを見ない。** 403 のエラーページでも「対応を記録する」は
  // 存在しないため、それだけでは壊れ方を判別できない。
  // **先に「開けたこと」を確かめる**（この形で実際に 403 を見逃した）
  await expect(page.getByRole('heading', { name: '例外の詳細' })).toBeVisible();
  await expect(page.getByText(trackingNumber)).toBeVisible();
  await expect(page.getByRole('button', { name: '対応を記録する' })).toHaveCount(0);
});
