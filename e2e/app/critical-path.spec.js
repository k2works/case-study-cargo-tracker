import { test, expect } from '@playwright/test';

/**
 * クリティカルパス: 予約 → 引き渡し → 経路確定 → 予約確定 → 追跡番号 → 積込 → 輸送中。
 *
 * **ここで確かめるのは「業務価値が実際に成立するか」だけである**
 * （`development_strategy.md` の横断方針 1）。個々の規則は単体・統合テストが
 * 見ており、E2E で繰り返さない。E2E が見るのは
 * **複数のロールをまたいで一本の業務が通ること**である。
 *
 * 4 人が順に登場する。営業担当者・経路設計者・営業担当者・追跡管理者・荷役作業員。
 * **ロールをまたぐ受け渡しは通知ではなく待ち行列で行う**（ADR-006）。
 * 待ち行列に現れることが、この業務における「引き渡し」である。
 */

const USERS = {
  sales: { username: 'sales', password: 'password' },
  router: { username: 'router', password: 'password' },
  tracker: { username: 'tracker', password: 'password' },
  handler: { username: 'handler', password: 'password' },
};

/**
 * ステータスバッジが指定の状態になっていることを確かめる.
 *
 * **フラッシュメッセージと取り違えない。** 「予約を登録しました（仮予約）」のような
 * 文にも状態名が含まれるため、素朴な文字列一致だと**操作の説明文を状態と誤認する**。
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} label 状態の日本語ラベル
 */
async function expectStatusBadge(page, label) {
  await expect(page.locator('.badge', { hasText: label }).first()).toBeVisible();
}

/**
 * 指定した利用者でログインする（前の利用者はログアウトする）.
 * @param {import('@playwright/test').Page} page ページ
 * @param {{username: string, password: string}} user 利用者
 */
async function loginAs(page, user) {
  await page.goto('/login');
  // すでにログイン済みならダッシュボードへ飛ばされる。**その場合は一度出る**
  if (!page.url().includes('/login')) {
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await page.goto('/login');
  }
  await page.fill('#username', user.username);
  await page.fill('#password', user.password);
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
}

test('予約から輸送開始までが一本つながる', async ({ page }) => {
  // **取り消せない操作には確認ダイアログがある**（経路の確定・キャンセル）。
  // 受け入れないとフォームが送信されず、押したつもりで何も起きない
  page.on('dialog', (dialog) => dialog.accept());

  // ---- 営業担当者: 予約を登録し、経路設計者に引き渡す ----
  await loginAs(page, USERS.sales);
  await page.getByRole('link', { name: '貨物予約' }).click();
  await page.getByRole('link', { name: '+ 新規予約登録' }).click();

  await page.fill('#shipperCode', 'SHP-000001');
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '1000');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'USLAX');
  const deadline = new Date();
  deadline.setDate(deadline.getDate() + 60);
  await page.fill('#arrivalDeadline', deadline.toISOString().slice(0, 10));
  await page.getByRole('button', { name: '登録する' }).click();

  await expectStatusBadge(page, '仮予約');
  const detailUrl = page.url();

  await page.getByRole('button', { name: '経路設計者に引き渡す' }).click();
  await expectStatusBadge(page, '経路提案済');

  // ---- 経路設計者: 候補を算出し、経路を確定する ----
  await loginAs(page, USERS.router);
  // **待ち行列から始める。** 引き渡しが成立していることをここで確かめる
  await page.getByRole('link', { name: '経路設計' }).click();
  await page.getByRole('row', { name: /JPOSA/ }).first()
    .getByRole('link', { name: '経路を割り当て' }).click();

  await page.getByRole('button', { name: '経路候補を算出する' }).click();
  // **算出は PRG である。** 戻ってきた画面で候補が出るまで待たずに押すと、
  // 押した先が古い画面になる
  await expect(page.getByRole('button', { name: 'この経路で確定' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);
  await expectStatusBadge(page, '割り当て済');

  // ---- 営業担当者: 予約を確定する ----
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.getByRole('button', { name: '予約を確定' }).click();
  await expectStatusBadge(page, '確認済');

  // ---- 追跡管理者: 追跡番号を発行する ----
  await loginAs(page, USERS.tracker);
  // **待ち行列から始める。** 通知が無くても発行の依頼が届いていることを確かめる
  await page.getByRole('link', { name: '追跡管理' }).click();
  await page.getByRole('link', { name: '予約を開いて発行' }).first().click();
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  await expectStatusBadge(page, '追跡番号発行済');

  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();
  expect(trackingNumber).toMatch(/^TRK-\d{8}-\d{4}$/);

  // ---- 荷役作業員: 積込を記録する ----
  await loginAs(page, USERS.handler);
  await page.getByRole('link', { name: '荷役管理' }).click();
  await page.getByRole('link', { name: '新規登録', exact: true }).click();

  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'LOAD');
  const workedAt = new Date();
  await page.fill('#completionTime', workedAt.toISOString().slice(0, 16));
  await page.fill('#locationUnlocode', 'JPOSA');
  await page.fill('#voyageNumber', 'V0001');
  await page.getByRole('button', { name: '登録する' }).click();

  // 登録した作業が先頭に出る（自分が今スキャンした荷物を探し直させない）
  await expect(page.getByRole('cell', { name: '積込' }).first()).toBeVisible();

  // ---- 営業担当者: 輸送が始まっている ----
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expectStatusBadge(page, '輸送中');
});
