import { test, expect } from '@playwright/test';

/**
 * 公開追跡（US18。認証不要）。
 *
 * **本システムで認証を持たない相手に見せる唯一の画面である。**
 * 荷主が取引先へ URL を転送するのは日常的に起きるため、
 * **見せてよい情報の範囲がそのまま設計上の制約になる**。
 *
 * ここで確かめるのは「ログインを一度もせずに開けること」である。
 * 表示の中身は統合テスト（`PublicTrackingInquiryTest`）が見ており、
 * E2E で繰り返さない。**E2E でしか確かめられないのは、ブラウザが
 * ログイン画面へ飛ばされないこと**である。
 */

test('未ログインのまま公開追跡を開ける', async ({ page }) => {
  await page.goto('/public/tracking');

  // **ログイン画面へ飛ばされない。** ここが E2E でしか確かめられない点である
  expect(page.url()).toContain('/public/tracking');
  await expect(page.getByRole('heading', { name: 'CargoTracker 公開追跡' })).toBeVisible();

  // **業務画面への導線を出さない。** 認証済みの利用者にしか意味がない
  await expect(page.getByRole('link', { name: '貨物予約' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'ログアウト' })).toHaveCount(0);
});

test('ログイン画面から公開追跡へ辿り着ける', async ({ page }) => {
  // **未認証の利用者にとっての作業入口はログイン画面である。**
  // navbar もダッシュボードも、認証済みの利用者にしか働かない。
  // 荷主から番号だけを伝えられた取引先は、ここまで来て行き止まりになっていた
  await page.goto('/login');

  await page.getByRole('link', { name: /追跡番号をお持ちの方/ }).click();

  await expect(page.getByRole('heading', { name: 'CargoTracker 公開追跡' })).toBeVisible();
  // **リンク切れを作らない。** クリックした先が実際に開けることまで確かめる
  expect(page.url()).toContain('/public/tracking');
});

test('存在しない追跡番号では同じことばが返る', async ({ page }) => {
  await page.goto('/public/tracking');

  // **形式は正しいが存在しない番号。** 「形式は正しい」と答えると、
  // 番号の総当たりで貨物の有無を確かめられる
  await page.fill('#trackingNumber', 'TRK-19990101-0001');
  await page.getByRole('button', { name: '追跡する' }).click();
  await expect(page.getByText('該当する貨物が見つかりません')).toBeVisible();

  // 形式そのものが違う番号でも**同じことば**が返る
  await page.fill('#trackingNumber', 'ABC');
  await page.getByRole('button', { name: '追跡する' }).click();
  await expect(page.getByText('該当する貨物が見つかりません')).toBeVisible();
});

test('照会した URL はそのまま共有できる', async ({ page }) => {
  await page.goto('/public/tracking');
  await page.fill('#trackingNumber', 'TRK-19990101-0001');
  await page.getByRole('button', { name: '追跡する' }).click();

  // **PRG を使わない。** リダイレクトで番号を URL から消すと、
  // 荷主が取引先へ転送できない。それが本画面の価値そのものである
  expect(page.url()).toContain('trackingNumber=TRK-19990101-0001');
});
