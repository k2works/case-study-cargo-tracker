import { test, expect } from '@playwright/test';
import { localDate } from './support/time.js';

/**
 * 危険物の予約から、取り扱える便だけを候補に見るまで（US05）。
 *
 * **申告の無い危険物を預かる形を作らない。** 危険物は法的要件を伴い、
 * 申告が無いまま輸送が始まると輸送書類が作れない。
 *
 * **取り扱える便の絞り込みは IT4（US08）で入っている。** ここではその判断が
 * 危険物の予約から実際に効くことを、画面をつないで確かめる。
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

test('危険物は申告を入れないと預かれず、運べる便だけが候補に出る', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  await loginAs(page, USERS.sales);
  await page.goto('/bookings/new');

  await page.fill('#shipperCode', 'SHP-000001');
  await page.fill('#weight', '800');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'USLAX');
  await page.fill('#arrivalDeadline', localDate(40));

  // 一般貨物では特別な入力欄を出さない。**押せない欄を見せない**
  await expect(page.getByText('危険物クラス')).toHaveCount(0);

  // 種別を危険物にすると申告の入力欄が現れる（htmx の部分更新）
  await page.selectOption('#cargoType', 'HAZARDOUS');
  await expect(page.getByLabel('危険物クラス')).toBeVisible();
  await expect(page.getByLabel('UN 番号')).toBeVisible();
  await expect(page.getByLabel('正式輸送品名')).toBeVisible();

  // **申告を入れずに送ると預かれない。** 画面から欄を消すだけでは
  // 細工した送信を防げないため、集約が拒む
  await page.getByRole('button', { name: '登録する' }).click();
  await expect(page.getByText(/危険物申告/).first()).toBeVisible();

  // 申告を入れて登録する
  await page.fill('#hazardClass', '3');
  await page.fill('#unNumber', 'UN1263');
  await page.fill('#properShippingName', 'PAINT');
  await page.getByRole('button', { name: '登録する' }).click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);

  // **記録しても見えなければ確認できない。** 予約詳細で申告を読める
  await expect(page.getByText('UN1263')).toBeVisible();
  await expect(page.getByText('PAINT')).toBeVisible();

  const detailUrl = page.url();
  await page.getByRole('button', { name: '経路設計者に引き渡す' }).click();

  // ---- 経路設計者: 危険物を運べる便だけが候補になる ----
  await loginAs(page, USERS.router);
  await page.goto(`${detailUrl}/route`);
  await page.getByRole('button', { name: '経路候補を算出する' }).click();

  // **危険物を扱えない便は選べない。** 候補から消すのではなく、
  // 選べない理由を出す（消すと「なぜ出ないのか」を確かめる手段が無くなる）
  const blockedRow = page.getByRole('row', { name: /この便は危険物を扱えません/ });
  await expect(blockedRow).toHaveCount(1);
  await expect(
    blockedRow.getByRole('button', { name: 'この経路で確定' }),
  ).toHaveCount(0);
});
