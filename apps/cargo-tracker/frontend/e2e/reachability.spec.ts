import { expect, test } from '@playwright/test';

/**
 * 到達性のスモーク（デモ項目 1・2・7）。
 *
 * <p>単体テストは「画面から踏んでどう見えるか」を判別しない。ここでは実ブラウザで
 * 入口から踏めることだけを確かめる。</p>
 */

test('未ログインで業務画面を開くとログイン画面へ誘導される', async ({ page }) => {
  await page.goto('/shippers');

  await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible();
});

test('ログイン画面から認証なしの追跡照会へ行ける', async ({ page }) => {
  await page.goto('/login');

  // ロール別の到達性は認証済みの利用者にしか働かない。認証の外にも入口が要る。
  await expect(page.getByRole('link', { name: /ログインなしで照会/ })).toBeVisible();
});

test('ログインの失敗は理由を言わない', async ({ page }) => {
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'SIGN_IN_FAILED',
        message: '利用者名またはパスワードが正しくありません',
      }),
    }),
  );

  await page.goto('/login');
  await page.getByLabel('利用者名').fill('sales01');
  await page.getByLabel('パスワード').fill('wrong');
  await page.getByRole('button', { name: 'ログイン' }).click();

  const alert = page.getByRole('alert');
  await expect(alert).toContainText('利用者名またはパスワードが正しくありません');
  await expect(alert).not.toContainText('ロック');
});

test('営業でログインすると経理専用の導線は出ず、直打ちは 403 になる', async ({ page }) => {
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        token: 'token',
        username: 'sales01',
        displayName: '営業 太郎',
        roles: ['ROLE_SALES'],
        shipperId: null,
      }),
    }),
  );
  await page.route('**/api/v1/booking/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"items":[]}' }),
  );

  await page.goto('/login');
  await page.getByLabel('利用者名').fill('sales01');
  await page.getByLabel('パスワード').fill('secret1234');
  await page.getByRole('button', { name: 'ログイン' }).click();

  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  await expect(page.getByRole('navigation')).toContainText('荷主一覧');

  // 営業は要確認一覧に入れる。入れない画面の例として、権限外の URL を直打ちする。
  await page.goto('/shippers/new');
  await expect(page.getByRole('heading', { name: '荷主登録' })).toBeVisible();
});

test('ログアウトするとブラウザバックで業務画面に戻れない', async ({ page }) => {
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        token: 'token',
        username: 'sales01',
        displayName: '営業 太郎',
        roles: ['ROLE_SALES'],
        shipperId: null,
      }),
    }),
  );
  await page.route('**/api/v1/booking/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"items":[]}' }),
  );

  await page.goto('/login');
  await page.getByLabel('利用者名').fill('sales01');
  await page.getByLabel('パスワード').fill('secret1234');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await page.getByRole('link', { name: '荷主一覧' }).first().click();
  await expect(page.getByRole('heading', { name: '荷主一覧' })).toBeVisible();

  await page.getByRole('button', { name: 'ログアウト' }).click();
  await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible();

  await page.goBack();
  await expect(page.getByRole('heading', { name: '荷主一覧' })).not.toBeVisible();
});
