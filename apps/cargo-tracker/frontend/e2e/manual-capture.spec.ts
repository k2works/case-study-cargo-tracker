import { expect, test } from '@playwright/test';

/**
 * マニュアルの画面キャプチャを生成する（creating-manual）。
 *
 * <p>手で撮ると、画面を変えたときに撮り直しを忘れる。古いキャプチャは、書いて
 * いないことよりも読者を迷わせるので、spec 経由でしか作らない。</p>
 *
 * <p>写す値は固定の見本データにする。日付や採番が毎回変わると、差分が本当の
 * 変化かどうか分からない。</p>
 */

const OUT = '../../../docs/manual/assets';

const SAMPLE_SHIPPERS = {
  items: [
    {
      shipperId: '11111111-1111-1111-1111-111111111111',
      shipperCode: 'SHP-000001',
      shipperType: 'CORPORATE',
      name: '山田商事',
      email: 'sales@example.com',
      phone: '03-1111-1111',
      address: '東京都中央区',
      contractNumber: 'CT-0001',
      discountRate: '0.1000',
    },
    {
      shipperId: '22222222-2222-2222-2222-222222222222',
      shipperCode: 'SHP-000002',
      shipperType: 'INDIVIDUAL',
      name: null,
      email: null,
      phone: null,
      address: null,
      contractNumber: null,
      discountRate: null,
    },
  ],
};

const SAMPLE_ATTENTION = {
  items: [
    {
      itemId: '33333333-3333-3333-3333-333333333333',
      kind: 'PROJECTION_REJECTED',
      targetType: 'SHIPPER',
      targetId: '44444444-4444-4444-4444-444444444444',
      assignedRole: 'ROLE_SALES',
      reason: 'メールアドレスの重複',
      occurredAt: '2026-09-03 09:00',
    },
  ],
};

test.describe('マニュアルの画面キャプチャ', () => {
  test.beforeEach(async ({ page }) => {
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
    await page.route('**/api/v1/booking/shippers**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_SHIPPERS),
      }),
    );
    await page.route('**/api/v1/booking/attention-items**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_ATTENTION),
      }),
    );
  });

  async function signIn(page: import('@playwright/test').Page) {
    await page.goto('/login');
    await page.getByLabel('利用者名').fill('sales01');
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  }

  test('02 ログイン画面', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible();
    await page.screenshot({ path: `${OUT}/02-S00-login.png`, fullPage: true });
  });

  test('02 ダッシュボード', async ({ page }) => {
    await signIn(page);
    await page.screenshot({ path: `${OUT}/02-S02-dashboard.png`, fullPage: true });
  });

  test('03 荷主一覧', async ({ page }) => {
    await signIn(page);
    await page.getByRole('link', { name: '荷主一覧' }).first().click();
    await expect(page.getByText('SHP-000001')).toBeVisible();
    // 削除済みの見え方もマニュアルで説明するので、同じ画面に写す。
    await expect(page.getByText('（削除済み）').first()).toBeVisible();
    await page.screenshot({ path: `${OUT}/03-S10-shipper-list.png`, fullPage: true });
  });

  test('03 荷主登録', async ({ page }) => {
    await signIn(page);
    await page.goto('/shippers/new');
    await expect(page.getByRole('heading', { name: '荷主登録' })).toBeVisible();
    await page.getByLabel('法人').check();
    await page.screenshot({ path: `${OUT}/03-S11-shipper-register.png`, fullPage: true });
  });

  test('04 要確認一覧', async ({ page }) => {
    await signIn(page);
    await page.goto('/worklist/attention');
    await expect(page.getByText('メールアドレスの重複')).toBeVisible();
    await page.screenshot({ path: `${OUT}/04-S70-attention-list.png`, fullPage: true });
  });
});
