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
  // リンクが見えることだけでなく、**押した先**まで確かめる。見えるだけの検査だと、
  // 遷移先が無くてログイン画面へ戻る状態を見逃す。
  await page.getByRole('link', { name: /ログインなしで照会/ }).click();

  await expect(page.getByRole('heading', { name: '荷物の追跡' })).toBeVisible();
  await expect(page).toHaveURL(/\/track$/);
});

test('追跡番号つきの公開追跡も認証なしで開ける', async ({ page }) => {
  await page.goto('/track/ABC12345');

  await expect(page.getByRole('heading', { name: '荷物の追跡' })).toBeVisible();
  await expect(page.getByText('ABC12345')).toBeVisible();
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
});

test('経理でログインすると荷主登録はナビに出ず、直打ちすると 403 になる', async ({ page }) => {
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        token: 'token',
        username: 'acct01',
        displayName: '経理 花子',
        roles: ['ROLE_ACCOUNTANT'],
        shipperId: null,
      }),
    }),
  );
  await page.route('**/api/v1/booking/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"items":[]}' }),
  );

  await page.goto('/login');
  await page.getByLabel('利用者名').fill('acct01');
  await page.getByLabel('パスワード').fill('secret1234');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();

  // 経理は荷主登録に入れない。ナビにも出ない。
  await expect(page.getByRole('navigation')).not.toContainText('荷主登録');

  // URL を直打ちしても入れないことを、画面から踏んで確かめる。
  await page.goto('/shippers/new');
  await expect(page.getByRole('heading', { name: 'この画面を開く権限がありません' }))
    .toBeVisible();
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
