import { test, expect } from '@playwright/test';
import path from 'path';

/**
 * ユーザーマニュアル（docs/manual/）の画面キャプチャを生成する。
 *
 * **これは検証ではなくドキュメント生成である。** アサーションは
 * 「撮影対象が描画し終わったこと」を待つためだけに置いている。
 * 待たずに撮ると描画途中が写る。
 *
 * 実在の取引先情報は載せない。データはシードユーザーと、
 * ここで登録するダミー荷主のみとする。
 */

const ASSETS = path.join(process.cwd(), 'docs', 'manual', 'assets');

/** シードされた営業担当者。ロール別の表示を撮るために使う。 */
const SALES = { username: 'sales', password: 'password' };

/**
 * 動作確認用の荷主。**アプリ側のシード（db/demo/V900__demo_shipper.sql）と同じ内容**である。
 *
 * ここで登録し直さずシードを使うのは、テストの実行順に依存させないためと、
 * マニュアルの図・開発環境の画面・キャプチャの 3 者を 1 つの出所に揃えるためである。
 * 実在の企業・個人は使わない。
 */
const DEMO_SHIPPER = {
  code: 'SHP-000001',
  name: '山田商事',
  email: 'shipper-sample@example.com',
};

/**
 * 指定した利用者でログインし、ダッシュボードが表示されるまで待つ.
 * @param {import('@playwright/test').Page} page ページ
 * @param {{username: string, password: string}} user 利用者
 */
async function login(page, user) {
  await page.goto('/login');
  await page.fill('#username', user.username);
  await page.fill('#password', user.password);
  await page.click('button[type="submit"]');
  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
}

/**
 * ページ全体を `docs/manual/assets/` に保存する.
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} name `<章番号>-<英字スラッグ>.png`
 */
async function capture(page, name) {
  await page.screenshot({ path: path.join(ASSETS, name), fullPage: true });
}

test('02-login（ログイン画面）', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible();
  await capture(page, '02-login.png');
});

test('02-login-error（認証エラーの表示）', async ({ page }) => {
  await page.goto('/login');
  await page.fill('#username', SALES.username);
  await page.fill('#password', 'wrong-password');
  await page.click('button[type="submit"]');
  await expect(page.getByText('利用者 ID またはパスワードが正しくありません')).toBeVisible();
  await capture(page, '02-login-error.png');
});

test('02-dashboard（ダッシュボード）', async ({ page }) => {
  await login(page, SALES);
  await expect(page.getByRole('link', { name: '荷主管理' }).first()).toBeVisible();
  await capture(page, '02-dashboard.png');
});

test('03-shipper-form（荷主登録）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers/new');
  await expect(page.getByRole('heading', { name: '荷主登録' })).toBeVisible();
  await capture(page, '03-shipper-form.png');
});

test('03-shipper-detail（荷主詳細）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers');
  // 一覧の「詳細」から開く。読者がマニュアルどおりに辿る経路と同じにする
  await page.getByRole('link', { name: '詳細' }).first().click();
  await expect(page.getByRole('heading', { name: '荷主詳細' })).toBeVisible();
  await expect(page.getByText(DEMO_SHIPPER.code)).toBeVisible();
  await capture(page, '03-shipper-detail.png');
});

test('03-shipper-list（荷主一覧）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers');
  // 空状態ではなく、代表的な 1 件が表示された状態で撮る
  await expect(page.getByText(DEMO_SHIPPER.name)).toBeVisible();
  await capture(page, '03-shipper-list.png');
});

test('03-shipper-duplicate（メールアドレスの重複）', async ({ page }) => {
  await login(page, SALES);
  await page.goto('/shippers/new');
  await page.check('#typeIndividual');
  await page.fill('#name', DEMO_SHIPPER.name);
  // シード済みのメールアドレスを入力して重複を再現する
  await page.fill('#email', DEMO_SHIPPER.email);
  await page.fill('#addressCountry', 'JP');
  await page.fill('#addressPostalCode', '100-0001');
  await page.fill('#addressRegion', '東京都');
  await page.fill('#addressCity', '千代田区');
  // ナビゲーションの「ログアウト」も submit ボタンである。名前で特定する
  await page.getByRole('button', { name: '登録' }).click();
  await expect(page.getByText('既に登録')).toBeVisible();
  await capture(page, '03-shipper-duplicate.png');
});
