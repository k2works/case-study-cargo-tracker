import { test, expect } from '@playwright/test';
import { localDate } from './support/time.js';

/**
 * 荷主が自社の予約だけを見る（US34）。
 *
 * **確かめる主眼は「見えること」より「見えないこと」にある。** IT2 で貨物予約一覧を
 * 荷主に開放したとき、利用者アカウントと荷主を結びつける手段が無く、他社の予約まで
 * 見える状態だった。以来 7 イテレーションにわたって「US34 で紐付けを作ってから開く」
 * と書き続けてきた。
 *
 * **他社の予約はこのテストの中で作る。** デモデータに他社を足して確かめると、
 * 「たまたま今のデータでは見えない」のか「絞り込みが効いている」のかを区別できない。
 */

const USERS = {
  sales: { username: 'sales', password: 'password' },
  shipper: { username: 'shipper', password: 'password' },
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

test('荷主の一覧には自社の予約だけが並ぶ', async ({ page }) => {
  const stamp = Date.now();
  const otherName = `他社商事${stamp}`;

  // ---- 営業担当者: 他社を登録し、その荷主の予約を作る ----
  await loginAs(page, USERS.sales);
  await page.goto('/shippers/new');
  await page.fill('#name', otherName);
  await page.fill('#email', `other-${stamp}@example.com`);
  await page.fill('#phone', '06-1234-5678');
  await page.fill('#addressCountry', 'JP');
  await page.fill('#addressPostalCode', '530-0001');
  await page.fill('#addressRegion', '大阪府');
  await page.fill('#addressCity', '大阪市北区');
  await page.fill('#addressStreet', '梅田 1-1-1');
  await page.getByRole('button', { name: '登録', exact: true }).click();

  // 採番された荷主コードを控える（予約はコードで結びつける）。
  // **登録した荷主の行から読む。** 一覧の先頭から読むと、既存の荷主
  // （SHP-000001 山田商事）を拾い、他社のつもりで自社の予約を作ってしまう
  // 登録後は荷主詳細に移る。**その画面のコードを読む**（一覧の先頭から読むと、
  // 既存の荷主 SHP-000001 山田商事 を拾い、他社のつもりで自社の予約を作る）
  await expect(page.getByText(otherName).first()).toBeVisible();
  const otherCode = (await page.locator('body').innerText()).match(/SHP-\d{6}/)[0];

  await page.goto('/bookings/new');
  await page.fill('#shipperCode', otherCode);
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '900');
  // **自社の予約と別の港にする。** 同じ港だと、絞り込みでなく
  // 検索条件の違いで消えたのか判断できない
  await page.fill('#origin', 'JPKIX');
  await page.fill('#destination', 'SGSIN');
  await page.fill('#arrivalDeadline', localDate(40));
  await page.getByRole('button', { name: '登録する' }).click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);
  const otherBookingId = page.url().split('/').pop();

  // ---- 荷主: 自社の予約だけが並ぶ ----
  await loginAs(page, USERS.shipper);

  // **作業入口から行く。** URL を直接叩けることは、業務が回ることを意味しない。
  // ダッシュボードの作業カードと navbar の両方から到達できる
  await expect(page.getByRole('heading', { name: '自社の予約' })).toBeVisible();
  await page.getByRole('link', { name: '自社の予約' }).click();
  await page.waitForURL(/\/bookings/);

  // 自社（山田商事）の予約は見える
  await expect(page.getByText('山田商事').first()).toBeVisible();
  // **他社の予約が 1 件でも現れたら失敗である**
  await expect(page.getByText(otherName)).toHaveCount(0);

  // 検索条件で他社を指定しても増えない（絞り込みは SQL で効く）
  await page.goto('/bookings?origin=JPKIX&destination=SGSIN');
  await expect(page.getByText(otherName)).toHaveCount(0);

  // **URL を直接指定しても開けない。** 403 ではなく 404 にする
  // （403 は「存在するが見せない」と伝えてしまう）
  const response = await page.goto(`/bookings/${otherBookingId}`);
  expect(response.status()).toBe(404);

  // 荷主は登録できない
  const forbidden = await page.goto('/bookings/new');
  expect(forbidden.status()).toBe(403);
});
