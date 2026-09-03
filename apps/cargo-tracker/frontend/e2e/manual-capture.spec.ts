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

const SAMPLE_BOOKINGS = {
  items: [
    {
      bookingId: '55555555-5555-5555-5555-555555555555',
      bookingNumber: 'B-2026-0903-0001',
      shipperId: '11111111-1111-1111-1111-111111111111',
      shipperName: '山田商事',
      originUnLocode: 'JPTYO',
      destinationUnLocode: 'USNYC',
      arrivalDeadline: '2026-12-01',
      cargoType: 'GENERAL',
      weightKg: '1200.00',
      lengthCm: '120.00',
      widthCm: '80.00',
      heightCm: '100.00',
      quantity: 10,
      productName: '自動車部品',
      hazardImoClass: null,
      hazardUnNumber: null,
      temperatureMinC: null,
      temperatureMaxC: null,
      bookingStatus: 'PRELIMINARY',
      routingStatus: 'NOT_ROUTED',
      bookedAt: '2026-09-03T01:00:00Z',
    },
  ],
  total: 1,
};

const SAMPLE_ADMIN_USERS = {
  users: [
    {
      username: 'sales01',
      displayName: '営業 太郎',
      roles: ['ROLE_SALES'],
      enabled: true,
      failedAttempts: 0,
      lockedUntil: null,
      locked: false,
    },
    {
      username: 'routing01',
      displayName: '経路 次郎',
      roles: ['ROLE_ROUTING'],
      enabled: true,
      failedAttempts: 5,
      lockedUntil: '2100-01-01T00:12:00Z',
      locked: true,
    },
    {
      username: 'handler01',
      displayName: '荷役 三郎',
      roles: ['ROLE_HANDLER'],
      enabled: true,
      failedAttempts: 3,
      lockedUntil: null,
      locked: false,
    },
    {
      username: 'retired01',
      displayName: '退職 済',
      roles: ['ROLE_SALES'],
      enabled: false,
      failedAttempts: 0,
      lockedUntil: null,
      locked: false,
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
    // 予約の経路。**Playwright の route は後に登録したものから当たる**ので、
    // 広いパターンを先に、限定的なパターンを後に置く。逆にすると、詳細の要求に
    // 一覧の形が返り「予約 undefined」になる（実測）。
    await page.route('**/api/v1/booking/bookings**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_BOOKINGS),
      }),
    );
    await page.route('**/api/v1/booking/bookings/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_BOOKINGS.items[0]),
      }),
    );
    await page.route('**/api/v1/booking/bookings/summary**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ preliminary: 3 }),
      }),
    );
    await page.route('**/api/v1/auth/admin/users**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_ADMIN_USERS),
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

  test('05 予約一覧', async ({ page }) => {
    await signIn(page);
    await page.getByRole('link', { name: '予約一覧' }).first().click();
    await expect(page.getByText('B-2026-0903-0001')).toBeVisible();
    await page.screenshot({ path: `${OUT}/05-S20-booking-list.png`, fullPage: true });
  });

  test('05 予約登録', async ({ page }) => {
    await signIn(page);
    await page.goto('/bookings/new');
    await expect(page.getByRole('heading', { name: '貨物予約の登録' })).toBeVisible();
    // 危険物を選んだときだけ現れる欄も説明するので、開いた状態で写す。
    await page.getByLabel('危険物').check();
    await page.screenshot({ path: `${OUT}/05-S21-booking-register.png`, fullPage: true });
  });

  test('05 予約詳細', async ({ page }) => {
    await signIn(page);
    await page.goto('/bookings/55555555-5555-5555-5555-555555555555');
    await expect(page.getByRole('heading', { name: /予約 B-2026-0903-0001/ })).toBeVisible();
    await page.screenshot({ path: `${OUT}/05-S22-booking-detail.png`, fullPage: true });
  });

  test('06 利用者管理', async ({ page }) => {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: 'token',
          username: 'admin01',
          displayName: '管理 花子',
          roles: ['ROLE_ADMIN'],
          shipperId: null,
        }),
      }),
    );
    await page.goto('/login');
    await page.getByLabel('利用者名').fill('admin01');
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await page.goto('/admin/users');
    await expect(page.getByText('routing01')).toBeVisible();
    await page.screenshot({ path: `${OUT}/06-S90-admin-users.png`, fullPage: true });
  });

  test('04 要確認一覧', async ({ page }) => {
    await signIn(page);
    await page.goto('/worklist/attention');
    await expect(page.getByText('メールアドレスの重複')).toBeVisible();
    await page.screenshot({ path: `${OUT}/04-S70-attention-list.png`, fullPage: true });
  });
});
