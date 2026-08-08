import { test, expect } from '@playwright/test';

/**
 * 候補ゼロから、条件を緩めて経路を見つけるまで（US10）。
 *
 * **候補ゼロは異常ではなく日常的に起きる**（業務代表の指摘）。期限が厳しい、
 * 対応港が限られる、といった理由で 1 件も出ないことは珍しくない。
 * 出ないまま行き止まりにすると、経路設計者は画面の外（電話・メール）で仕事をすることになる。
 *
 * **ここで確かめるのは業務が前に進むことだけである。** 緩和量の上限や当初期限の保持は
 * 単体・統合テストが見ており、E2E では繰り返さない。
 */

const USERS = {
  sales: { username: 'sales', password: 'password' },
  router: { username: 'router', password: 'password' },
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

test('期限が厳しくても条件を緩めれば経路が見つかる', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  // ---- 営業担当者: 期限の厳しい予約を登録して引き渡す ----
  await loginAs(page, USERS.sales);
  await page.getByRole('link', { name: '貨物予約' }).click();
  await page.getByRole('link', { name: '+ 新規予約登録' }).click();

  await page.fill('#shipperCode', 'SHP-000001');
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '800');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'USLAX');
  // **わざと厳しい期限にする。** これが現場で日常的に起きる形である
  const tight = new Date();
  tight.setDate(tight.getDate() + 3);
  await page.fill('#arrivalDeadline', tight.toISOString().slice(0, 10));
  await page.getByRole('button', { name: '登録する' }).click();

  const detailUrl = page.url();
  await page.getByRole('button', { name: '経路設計者に引き渡す' }).click();

  // ---- 経路設計者: 算出する。期限に間に合う候補が無い ----
  await loginAs(page, USERS.router);
  await page.goto(`${detailUrl}/route`);
  await page.getByRole('button', { name: '経路候補を算出する' }).click();

  // **候補ゼロも「期限に間に合わない」も行き止まりにしない。**
  // どちらの場合も、次にできること（条件を緩める）が同じ画面にある
  await expect(page.getByRole('heading', { name: '探索条件' })).toBeVisible();
  await expect(page.getByRole('button', { name: '+7 日' })).toBeVisible();

  // ---- 経路設計者: 期限を 7 日延ばして探し直す ----
  await page.getByRole('button', { name: '+7 日' }).click();

  // **延ばした事実が画面に残る。** 記録しても見せなければ、
  // 荷主への通知（US12）に載せる担当者が気づけない
  await expect(page.getByText(/当初の希望期限から\s*7\s*日延ばして探しています/))
    .toBeVisible();

  // 期限を緩めたことで確定できる候補が現れる
  await expect(page.getByRole('button', { name: 'この経路で確定' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);
  await expect(page.locator('.badge', { hasText: '割り当て済' }).first()).toBeVisible();
});
