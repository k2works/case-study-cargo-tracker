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

const SAMPLE_VOYAGES = {
  items: [
    {
      voyageNumber: 'V-MOL-001',
      carrierCode: 'MOL',
      carrierName: '商船三井',
      vesselName: 'MOL EXPRESS',
      departureUnLocode: 'JPTYO',
      arrivalUnLocode: 'USNYC',
      departureAt: '2026-09-10T09:00:00Z',
      arrivalAt: '2026-09-24T18:00:00Z',
      cancelled: false,
      acceptedCargoTypes: ['GENERAL', 'HAZARDOUS'],
      updatedAt: '2026-09-05T02:00:00Z',
      updatedBy: 'routing01',
      movements: [
        {
          movementSeq: 1,
          departureUnLocode: 'JPTYO',
          arrivalUnLocode: 'USNYC',
          departureAt: '2026-09-10T09:00:00Z',
          arrivalAt: '2026-09-24T18:00:00Z',
        },
      ],
    },
    {
      voyageNumber: 'V-ONE-118',
      carrierCode: 'ONE',
      carrierName: 'ONE',
      vesselName: 'ONE HARMONY',
      departureUnLocode: 'JPTYO',
      arrivalUnLocode: 'SGSIN',
      departureAt: '2026-09-12T15:30:00Z',
      arrivalAt: '2026-09-19T08:00:00Z',
      cancelled: false,
      acceptedCargoTypes: ['GENERAL', 'REEFER'],
      updatedAt: null,
      updatedBy: null,
      movements: [],
    },
  ],
  total: 2,
};

/**
 * 経路が確定していて、一度修正した予約（05 章）。
 *
 * <p>仮受付の見本（{@code SAMPLE_BOOKINGS.items[0]}）では「旅程」も「修正履歴」も
 * 出ない。マニュアルはその両方を説明しているので、説明だけがあって画面に無い状態に
 * なる（IT5 引き継ぎ 1）。<b>2 つの状態は 1 枚に収まらないので、別のキャプチャにする。</b></p>
 */
const SAMPLE_ROUTED_BOOKING = {
  ...SAMPLE_BOOKINGS.items[0],
  bookingId: '77777777-7777-7777-7777-777777777777',
  bookingNumber: 'B-2026-0903-0002',
  bookingStatus: 'ROUTE_PROPOSED',
  routingStatus: 'ROUTED',
  updatedAt: '2026-09-05T02:00:00Z',
  updatedBy: 'sales02',
};

/** 確定した旅程。乗り継ぎのある形にする。区間が 1 本だと積む順が読めない。 */
const SAMPLE_ITINERARY = {
  legs: [
    {
      legSeq: 1,
      voyageNumber: 'V-ONE-118',
      loadUnLocode: 'JPTYO',
      unloadUnLocode: 'SGSIN',
      loadAt: '2026-09-12T15:30:00Z',
      unloadAt: '2026-09-19T08:00:00Z',
    },
    {
      legSeq: 2,
      voyageNumber: 'V-MOL-001',
      loadUnLocode: 'SGSIN',
      unloadUnLocode: 'USNYC',
      loadAt: '2026-09-20T01:00:00Z',
      unloadAt: '2026-10-05T18:00:00Z',
    },
  ],
};

/** 経路設計作業一覧に出す予約。誤配を先頭に置いて並び順も写す。 */
const SAMPLE_WORKLIST = {
  items: [
    {
      ...SAMPLE_BOOKINGS.items[0],
      bookingId: '66666666-6666-6666-6666-666666666666',
      bookingNumber: 'B-2026-0902-0007',
      productName: '塗料',
      cargoType: 'HAZARDOUS',
      bookingStatus: 'ROUTE_PROPOSED',
      routingStatus: 'MISROUTED',
      arrivalDeadline: '2026-09-30',
      routingRequestedAt: '2026-09-02T00:30:00Z',
    },
    {
      ...SAMPLE_BOOKINGS.items[0],
      bookingStatus: 'ROUTE_PROPOSED',
      routingStatus: 'ROUTING_REQUESTED',
      routingRequestedAt: '2026-09-04T05:10:00Z',
    },
  ],
  total: 2,
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
    // 経路が確定した予約（05 章）。**1 件の読み口より後**に置く。`*` は `/` を
    // またがないので、これを先に置くと当たらない。
    await page.route('**/api/v1/booking/bookings/77777777-*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_ROUTED_BOOKING),
      }),
    );
    // 航海を止める前の影響範囲（S34 / US24）。**1 件の読み口より後**に置く。
    await page.route('**/api/v1/booking/bookings/by-voyage/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              bookingId: '77777777-7777-7777-7777-777777777777',
              bookingNumber: 'B-2026-0903-0002',
              bookingStatus: 'ROUTE_PROPOSED',
              routingStatus: 'ROUTED',
            },
          ],
        }),
      }),
    );
    await page.route('**/api/v1/booking/bookings/*/itinerary', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_ITINERARY),
      }),
    );
    // 見直し依頼（S02 / US10）。**1 件の読み口より後**に置く。逆にすると
    // `bookings/*` に当たって予約 1 件の形が返り、ダッシュボードが丸ごと落ちる。
    await page.route('**/api/v1/booking/bookings/condition-reviews**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              bookingId: '99999999-9999-9999-9999-999999999999',
              bookingNumber: 'B-2026-0902-0011',
              reason: '期限内に着ける便がありません',
              requestedAt: '2026-09-05T23:30:00Z',
            },
          ],
        }),
      }),
    );
    await page.route('**/api/v1/booking/bookings/summary**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ preliminary: 3, awaitingNotification: 2 }),
      }),
    );
    await page.route('**/api/v1/routing/voyages**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_VOYAGES),
      }),
    );
    // 航海 1 件（S34・S33 の更新）。**広いパターンより後に置く**。逆にすると
    // 詳細の要求に一覧の形が返り、画面が「取得できません」になる。
    await page.route('**/api/v1/routing/voyages/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_VOYAGES.items[0]),
      }),
    );
    // 更新前の差分。**1 件の読み口より後**に置く（`*` は `/` をまたがない）。
    await page.route('**/api/v1/routing/voyages/*/diff', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          voyageNumber: 'V-MOL-001',
          changes: [{ label: '船名', before: 'MOL EXPRESS', after: 'MOL VICTORY' }],
        }),
      }),
    );
    await page.route('**/api/v1/booking/bookings/routing-worklist**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_WORKLIST),
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
    // 要確認は BC ごとの読み口を画面が束ねる。**片方を用意しないと画面全体が
    // 失敗になる**（束ねる側はどれか 1 つでも取れないと出さない）。
    await page.route('**/api/v1/routing/attention-items**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
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
    // **一覧のモックより後に置く。** `**/api/v1/booking/bookings**` は `/` を
    // またぐので、明示しないと修正履歴の問い合わせにも一覧が返り、予約の行が
    // 「変更前も変更後も空の修正」として表に並ぶ（実測）。
    await page.route('**/api/v1/booking/bookings/*/revisions', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              updatedAt: '2026-09-05T02:00:00Z',
              updatedBy: 'sales02',
              label: '品名',
              before: '自動車部品（誤記）',
              after: '自動車部品',
            },
          ],
        }),
      }),
    );
    await signIn(page);
    await page.goto('/bookings/55555555-5555-5555-5555-555555555555');
    await expect(page.getByRole('heading', { name: /予約 B-2026-0903-0001/ })).toBeVisible();
    await page.screenshot({ path: `${OUT}/05-S22-booking-detail.png`, fullPage: true });
  });

  test('05 予約詳細（経路確定済み・修正済み）', async ({ page }) => {
    // **仮受付の見本では「旅程」も「修正履歴」も出ない。** マニュアルはその両方を
    // 説明しているので、1 枚では説明と画面が食い違う（IT5 引き継ぎ 1）。
    await page.route('**/api/v1/booking/bookings/*/revisions', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              updatedAt: '2026-09-05T02:00:00Z',
              updatedBy: 'sales02',
              label: '到着期限',
              before: '2026-11-20',
              after: '2026-12-01',
            },
          ],
        }),
      }),
    );
    await signIn(page);
    await page.goto('/bookings/77777777-7777-7777-7777-777777777777');
    await expect(page.getByRole('heading', { name: /予約 B-2026-0903-0002/ })).toBeVisible();
    // 撮る前に、写したい 2 つが出ていることを確かめる。出ていないまま撮ると、
    // 説明と食い違うキャプチャを作り直しただけになる。
    await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '修正履歴' })).toBeVisible();
    await page.screenshot({
      path: `${OUT}/05-S22-booking-detail-routed.png`,
      fullPage: true,
    });
  });

  test('10 荷主への通知', async ({ page }) => {
    // 通知の欄は経路が決まった予約にだけ出る。通知履歴も一緒に写す
    // （マニュアルが両方を説明しているため）。
    await page.route('**/api/v1/booking/bookings/*/notifications', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              notifiedAt: '2026-09-06T02:00:00Z',
              recipientEmail: 'sales@example.com',
              summary: 'JPTYO → SGSIN → USNYC / 所要 24 日 / 到着予定 2026/10/06 03:00',
              notifiedBy: 'sales01',
            },
          ],
        }),
      }),
    );
    await page.route('**/api/v1/booking/bookings/88888888-*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...SAMPLE_ROUTED_BOOKING,
          bookingId: '88888888-8888-8888-8888-888888888888',
          bookingNumber: 'B-2026-0903-0003',
          bookingStatus: 'ROUTE_NOTIFIED',
          lastNotifiedAt: '2026-09-06T02:00:00Z',
          updatedAt: null,
          updatedBy: null,
        }),
      }),
    );
    await signIn(page);
    await page.goto('/bookings/88888888-8888-8888-8888-888888888888');
    await expect(page.getByRole('heading', { name: '荷主への通知' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '通知履歴' })).toBeVisible();
    await page.screenshot({ path: `${OUT}/10-S22-notify-shipper.png`, fullPage: true });
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

  /** 経路設計者としてログインする。航海と作業一覧はこのロールの画面。 */
  async function signInAsRouting(page: import('@playwright/test').Page) {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: 'token',
          username: 'routing01',
          displayName: '経路 次郎',
          roles: ['ROLE_ROUTING'],
          shipperId: null,
        }),
      }),
    );
    await page.goto('/login');
    await page.getByLabel('利用者名').fill('routing01');
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  }

  test('07 航海スケジュール一覧', async ({ page }) => {
    await signInAsRouting(page);
    await page.goto('/voyages');
    await expect(page.getByText('V-MOL-001')).toBeVisible();
    await page.screenshot({ path: `${OUT}/07-S32-voyage-list.png`, fullPage: true });
  });

  test('07 航海スケジュール登録', async ({ page }) => {
    await signInAsRouting(page);
    await page.goto('/voyages/new');
    await expect(page.getByRole('heading', { name: '航海スケジュールを登録する' })).toBeVisible();
    // 寄港地を 2 区間にした状態で写す。マニュアルが「行を増やす」を説明するため。
    await page.getByRole('button', { name: '寄港地を追加する' }).click();
    await page.screenshot({ path: `${OUT}/07-S33-voyage-register.png`, fullPage: true });
  });

  test('08 経路設計作業一覧', async ({ page }) => {
    await signInAsRouting(page);
    await page.goto('/routing/worklist');
    await expect(page.getByText('B-2026-0902-0007')).toBeVisible();
    await page.screenshot({ path: `${OUT}/08-S30-routing-worklist.png`, fullPage: true });
  });

  test('08 予約詳細（引き渡したあと）', async ({ page }) => {
    // **05 章と同じ状態を撮らない。** 同じ画面の同じ状態を 2 章に貼ると、
    // 読者はどちらの説明を見ているのか分からなくなる（IT3 レビュー R.6）。
    // 05 章は仮受付（引き渡しのボタンが出ている）、この章は押したあとを写す。
    await page.route('**/api/v1/booking/bookings/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...SAMPLE_BOOKINGS.items[0],
          bookingStatus: 'ROUTE_PROPOSED',
          routingStatus: 'ROUTING_REQUESTED',
          routingRequestedAt: '2026-09-04T05:10:00Z',
          // **条件は予約が持つ**（IT7 H.1）。候補算出の応答に載せると、探索が
          // 落ちている間だけ条件の欄と差し戻しが画面から消える。
          routeExcludeUnLocodes: ['SGSIN'],
          routeDepartFromUnLocode: null,
        }),
      }),
    );
    await signIn(page);
    await page.goto('/bookings/55555555-5555-5555-5555-555555555555');
    await expect(page.getByText('経路提案中')).toBeVisible();
    await page.screenshot({ path: `${OUT}/08-S22-request-routing.png`, fullPage: true });
  });

  test('05 予約修正', async ({ page }) => {
    await signIn(page);
    await page.goto('/bookings/55555555-5555-5555-5555-555555555555/edit');
    await expect(page.getByRole('heading', { name: '予約を修正する' })).toBeVisible();
    await page.screenshot({ path: `${OUT}/05-S24-booking-edit.png`, fullPage: true });
  });

  test('07 航海詳細', async ({ page }) => {
    await signInAsRouting(page);
    await page.goto('/voyages/V-MOL-001');
    await expect(page.getByRole('heading', { name: '航海 V-MOL-001' })).toBeVisible();
    await page.screenshot({ path: `${OUT}/07-S34-voyage-detail.png`, fullPage: true });
  });

  test('07 航海スケジュール更新（差分の確認）', async ({ page }) => {
    await signInAsRouting(page);
    await page.goto('/voyages/V-MOL-001/edit');
    await expect(page.getByRole('heading', { name: '航海スケジュールを更新する' })).toBeVisible();
    // 差分を出した状態で写す。マニュアルが「確かめてから送る」を説明するため。
    await page.getByLabel('船名').fill('MOL VICTORY');
    await page.getByRole('button', { name: '差分を確認する' }).click();
    await expect(page.getByText('更新の内容')).toBeVisible();
    await page.screenshot({ path: `${OUT}/07-S33-voyage-update.png`, fullPage: true });
  });

  test('04 要確認一覧', async ({ page }) => {
    await signIn(page);
    await page.goto('/worklist/attention');
    await expect(page.getByText('メールアドレスの重複')).toBeVisible();
    await page.screenshot({ path: `${OUT}/04-S70-attention-list.png`, fullPage: true });
  });

  test('11 予約の確定（営業）', async ({ page }) => {
    // 通知済みの予約。**営業に「予約を確定する」が出る**（US13）。
    await page.route('**/api/v1/booking/bookings/*/notifications', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ items: [] }) }));
    await page.route('**/api/v1/booking/bookings/*/itinerary', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify(SAMPLE_ITINERARY) }));
    await page.route('**/api/v1/booking/bookings/*/revisions', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ items: [] }) }));
    await page.route('**/api/v1/booking/bookings/*', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ ...SAMPLE_ROUTED_BOOKING,
          bookingStatus: 'ROUTE_NOTIFIED',
          lastNotifiedAt: '2026-09-07T00:00:00Z' }) }));

    await signIn(page);
    await page.goto('/bookings/55555555-5555-5555-5555-555555555555');
    await expect(page.getByRole('heading', { name: '予約の確定' })).toBeVisible();
    await page.screenshot({ path: `${OUT}/11-S22-confirm-booking.png`, fullPage: true });
  });

  test('11 追跡番号の発行（経路設計）', async ({ page }) => {
    // 確定済みの予約。**経路設計者に「追跡番号を発行する」が出る**（US14）。
    await page.route('**/api/v1/booking/bookings/*/notifications', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ items: [] }) }));
    await page.route('**/api/v1/booking/bookings/*/itinerary', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify(SAMPLE_ITINERARY) }));
    await page.route('**/api/v1/booking/bookings/*/revisions', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ items: [] }) }));
    await page.route('**/api/v1/booking/bookings/*', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ ...SAMPLE_ROUTED_BOOKING,
          bookingStatus: 'CONFIRMED',
          lastNotifiedAt: '2026-09-07T00:00:00Z',
          confirmedAt: '2026-09-08T00:00:00Z' }) }));

    await signInAsRouting(page);
    await page.goto('/bookings/55555555-5555-5555-5555-555555555555');
    await expect(page.getByRole('heading', { name: '追跡番号の発行' })).toBeVisible();
    await page.screenshot({ path: `${OUT}/11-S22-issue-tracking-number.png`, fullPage: true });
  });

  test('09 経路設計ワークベンチ', async ({ page }) => {
    // 引き渡したあとの予約に候補が並んだ状態を写す。**1 件の読み口より後に置く**
    // （`*` は `/` をまたがないので、広いパターンが先だと候補の経路に当たらない）。
    await page.route('**/api/v1/booking/bookings/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...SAMPLE_BOOKINGS.items[0],
          bookingStatus: 'ROUTE_PROPOSED',
          routingStatus: 'ROUTING_REQUESTED',
          routingRequestedAt: '2026-09-04T05:10:00Z',
          // **条件は予約が持つ**（IT7 H.1）。候補算出の応答に載せると、探索が
          // 落ちている間だけ条件の欄と差し戻しが画面から消える。
          routeExcludeUnLocodes: ['SGSIN'],
          routeDepartFromUnLocode: null,
        }),
      }),
    );
    await page.route('**/api/v1/booking/bookings/*/route-candidates', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          candidates: [
            {
              legs: [
                {
                  voyageNumber: 'V-MOL-001',
                  loadUnLocode: 'JPTYO',
                  unloadUnLocode: 'USNYC',
                  loadTime: '2026-09-20T09:00:00Z',
                  unloadTime: '2026-10-09T18:00:00Z',
                },
              ],
              transitDays: 19,
              direct: true,
            },
            {
              legs: [
                {
                  voyageNumber: 'V-MOL-002',
                  loadUnLocode: 'JPTYO',
                  unloadUnLocode: 'SGSIN',
                  loadTime: '2026-09-20T09:00:00Z',
                  unloadTime: '2026-09-28T08:00:00Z',
                },
                {
                  voyageNumber: 'V-MSK-220',
                  loadUnLocode: 'SGSIN',
                  unloadUnLocode: 'USNYC',
                  loadTime: '2026-09-29T06:00:00Z',
                  unloadTime: '2026-10-12T18:00:00Z',
                },
              ],
              transitDays: 22,
              direct: false,
            },
          ],
          truncated: false,
        }),
      }),
    );
    await signInAsRouting(page);
    await page.goto('/routing/bookings/55555555-5555-5555-5555-555555555555');
    await expect(page.getByRole('heading', { name: '経路候補' })).toBeVisible();
    await expect(page.getByTestId('candidate-1')).toBeVisible();
    await page.screenshot({ path: `${OUT}/09-S31-routing-workbench.png`, fullPage: true });
  });

  async function signInAsTracker(page: import('@playwright/test').Page) {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: 'token',
          username: 'tracker01',
          displayName: '追跡 三郎',
          roles: ['ROLE_TRACKER'],
          shipperId: null,
        }),
      }),
    );
    await page.goto('/login');
    await page.getByLabel('利用者名').fill('tracker01');
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  }

  const SAMPLE_TRACKING = {
    trackingNumber: 'TRK-AB12CD3456',
    bookingId: '55555555-5555-5555-5555-555555555555',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    cargoType: 'GENERAL',
    status: 'LOADED',
    statusLabel: '積込済',
    currentUnLocode: 'JPTYO',
    estimatedArrival: '2026-10-12T18:00:00Z',
    lastStatusChangedAt: '2026-09-21T00:00:00Z',
    history: [
      {
        occurredAt: '2026-09-20T01:00:00Z',
        eventType: 'MANUAL',
        previousStatusLabel: '未受領',
        statusLabel: '受領済',
        location: 'JPTYO',
        recordedBy: 'tracker01',
      },
      {
        occurredAt: '2026-09-21T00:00:00Z',
        eventType: 'MANUAL',
        previousStatusLabel: '受領済',
        statusLabel: '積込済',
        location: 'JPTYO',
        recordedBy: 'tracker01',
      },
    ],
    // **本文が説明する要素を写す。** 12 章は例外に触れないので空のままにする
    // ——ここに例外があると、12 章の画像に 14 章の話が写り込む。
    exceptions: [],
    nextStatuses: ['IN_TRANSIT', 'UNLOADED', 'AWAITING_CLAIM', 'MISROUTED', 'EXCEPTION'],
  };

  test('12 公開追跡照会', async ({ page }) => {
    // **認証の外の画面。** ログインせずに開く。
    await page.route('**/api/v1/tracking/public/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          trackingNumber: 'TRK-AB12CD3456',
          originUnLocode: 'JPTYO',
          destinationUnLocode: 'USNYC',
          statusLabel: '積込済',
          currentUnLocode: 'JPTYO',
          departedAt: '2026-09-21T00:00:00Z',
          estimatedArrival: '2026-10-12T18:00:00Z',
          history: SAMPLE_TRACKING.history.map((event) => ({
            occurredAt: event.occurredAt,
            statusLabel: event.statusLabel,
            location: event.location,
          })),
        }),
      }),
    );
    await page.goto('/track/TRK-AB12CD3456');
    await expect(page.getByText('積込済').first()).toBeVisible();
    await page.screenshot({ path: `${OUT}/12-S44-public-tracking.png`, fullPage: true });
  });

  test('12 追跡一覧', async ({ page }) => {
    await page.route('**/api/v1/tracking/trackings?*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          total: 2,
          items: [
            {
              trackingNumber: 'TRK-AB12CD3456',
              originUnLocode: 'JPTYO',
              destinationUnLocode: 'USNYC',
              statusLabel: '積込済',
              currentUnLocode: 'JPTYO',
              estimatedArrival: '2026-10-12T18:00:00Z',
              lastStatusChangedAt: '2026-09-21T00:00:00Z',
            },
            {
              trackingNumber: 'TRK-EF78GH9012',
              originUnLocode: 'JPOSA',
              destinationUnLocode: 'DEHAM',
              statusLabel: '輸送中',
              currentUnLocode: 'SGSIN',
              estimatedArrival: '2026-10-20T10:00:00Z',
              lastStatusChangedAt: '2026-09-22T00:00:00Z',
            },
          ],
        }),
      }),
    );
    await signInAsTracker(page);
    await page.goto('/tracking');
    await expect(page.getByRole('link', { name: 'TRK-AB12CD3456' })).toBeVisible();
    await page.screenshot({ path: `${OUT}/12-S40-tracking-list.png`, fullPage: true });
  });

  test('12 追跡詳細', async ({ page }) => {
    await page.route('**/api/v1/tracking/trackings/TRK-AB12CD3456', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SAMPLE_TRACKING),
      }),
    );
    await signInAsTracker(page);
    await page.goto('/tracking/TRK-AB12CD3456');
    await expect(page.getByRole('heading', { name: /TRK-AB12CD3456/ })).toBeVisible();
    await expect(page.getByLabel('新しい状態')).toBeVisible();
    await page.screenshot({ path: `${OUT}/12-S41-tracking-detail.png`, fullPage: true });
  });

  async function signInAsHandler(page: import('@playwright/test').Page) {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: 'token',
          username: 'handler01',
          displayName: '荷役 四郎',
          roles: ['ROLE_HANDLER'],
          shipperId: null,
        }),
      }),
    );
    await page.goto('/login');
    await page.getByLabel('利用者名').fill('handler01');
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  }

  test('13 荷役の記録', async ({ page }) => {
    await page.route('**/api/v1/handling/voyages/*/cargos*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              trackingNumber: 'TRK-AB12CD3456',
              bookingId: 'b-1',
              originUnLocode: 'JPTYO',
              destinationUnLocode: 'USNYC',
              cargoType: 'GENERAL',
              handledTypes: [],
            },
            {
              trackingNumber: 'TRK-EF78GH9012',
              bookingId: 'b-2',
              originUnLocode: 'JPOSA',
              destinationUnLocode: 'USNYC',
              cargoType: 'GENERAL',
              handledTypes: ['UNLOAD'],
            },
          ],
        }),
      }),
    );
    // **本文が説明する要素を写す。** 「確認」欄（区間・貨物種別）と予定外の
    // 先出し警告は、追跡番号を入れないと出ない。初期状態のまま撮ると、
    // 文章と画像が別々に正しくなる（IT9 レビュー writer 中 6）。
    await page.route('**/api/v1/handling/cargos/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          trackingNumber: 'TRK-AB12CD3456',
          bookingId: 'b-1',
          originUnLocode: 'JPTYO',
          destinationUnLocode: 'USNYC',
          cargoType: 'GENERAL',
          // 予定は東京 → ニューヨーク。シンガポールで降ろすのは予定外。
          legs: [{ voyageNumber: 'V-MOL-001', loadUnLocode: 'JPTYO', unloadUnLocode: 'USNYC' }],
          // **判定はサーバが答える**（H.5）。シンガポールでの荷降しは予定外。
          offRouteByType: { RECEIVE: true, LOAD: true, UNLOAD: true, CLAIM: true },
        }),
      }),
    );
    await signInAsHandler(page);
    await page.goto('/handling/voyages/V-MOL-001?unLocode=SGSIN');
    await expect(page.getByRole('heading', { name: /荷役の記録/ })).toBeVisible();
    await page.getByLabel('追跡番号').fill('TRK-AB12CD3456');
    await expect(page.getByText('確認', { exact: true })).toBeVisible();
    await expect(page.getByText(/予定ルートに含まれていません/)).toBeVisible();
    await page.screenshot({ path: `${OUT}/13-S50-handling-record.png`, fullPage: true });
  });

  test('13 荷役履歴', async ({ page }) => {
    await page.route('**/api/v1/handling/*/activities', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          trackingNumber: 'TRK-AB12CD3456',
          items: [
            {
              activityId: 'act-1',
              handlingType: 'RECEIVE',
              handlingTypeLabel: '受領',
              unLocode: 'JPTYO',
              voyageNumber: null,
              offRoute: false,
              operator: 'handler01',
              completedAt: '2026-09-20T01:00:00Z',
              voided: false,
              voidedAt: null,
              voidedBy: null,
              consigneeName: null,
              voidReason: null,
            },
            {
              activityId: 'act-2',
              handlingType: 'LOAD',
              handlingTypeLabel: '積込',
              unLocode: 'JPTYO',
              voyageNumber: 'V-MOL-001',
              offRoute: false,
              operator: 'handler01',
              completedAt: '2026-09-21T00:00:00Z',
              voided: false,
              voidedAt: null,
              voidedBy: null,
              consigneeName: null,
              voidReason: null,
            },
            // **本文が「取り消した記録も出ます」「予定外の印が付きます」と
            // 書いている。** 記録済の行だけを写すと、文章と画像が別々に
            // 正しくなる（IT9 レビュー writer 中 7）。
            {
              activityId: 'act-3',
              handlingType: 'UNLOAD',
              handlingTypeLabel: '荷降し',
              unLocode: 'SGSIN',
              voyageNumber: 'V-MOL-001',
              offRoute: true,
              operator: 'handler02',
              completedAt: '2026-09-26T02:00:00Z',
              voided: false,
              voidedAt: null,
              voidedBy: null,
              consigneeName: null,
              voidReason: null,
            },
            {
              activityId: 'act-4',
              handlingType: 'UNLOAD',
              handlingTypeLabel: '荷降し',
              unLocode: 'DEHAM',
              voyageNumber: 'V-MOL-001',
              offRoute: false,
              operator: 'handler02',
              completedAt: '2026-09-27T02:00:00Z',
              voided: true,
              voidedAt: '2026-09-27T03:00:00Z',
              voidedBy: 'handler01',
              consigneeName: null,
              voidReason: '別の貨物と取り違えました',
            },
          ],
        }),
      }),
    );
    await signInAsHandler(page);
    await page.goto('/handling/TRK-AB12CD3456');
    await expect(page.getByRole('heading', { name: /荷役履歴/ })).toBeVisible();
    await page.screenshot({ path: `${OUT}/13-S51-handling-history.png`, fullPage: true });
  });

  test('14 引取の記録', async ({ page }) => {
    // **本文が説明する要素を写す。** 荷受人の確認の欄は「引取」を選ばないと
    // 出ない。初期状態のまま撮ると、文章と画像が別々に正しくなる。
    await page.route('**/api/v1/handling/voyages/*/cargos*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [{
            trackingNumber: 'TRK-AB12CD3456',
            bookingId: 'b-1',
            originUnLocode: 'JPTYO',
            destinationUnLocode: 'USNYC',
            cargoType: 'GENERAL',
            handledTypes: ['UNLOAD'],
          }],
        }),
      }),
    );
    await page.route('**/api/v1/handling/cargos/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          trackingNumber: 'TRK-AB12CD3456',
          bookingId: 'b-1',
          originUnLocode: 'JPTYO',
          destinationUnLocode: 'USNYC',
          cargoType: 'GENERAL',
          legs: [{ voyageNumber: 'V-MOL-001', loadUnLocode: 'JPTYO', unloadUnLocode: 'USNYC' }],
          // 目的港での引取は予定どおり。
          offRouteByType: { RECEIVE: true, LOAD: true, UNLOAD: false, CLAIM: false },
        }),
      }),
    );
    await signInAsHandler(page);
    await page.goto('/handling/voyages/V-MOL-001?unLocode=USNYC');
    await page.getByLabel('作業種別').selectOption('CLAIM');
    await page.getByLabel('追跡番号').fill('TRK-AB12CD3456');
    await expect(page.getByText('確認', { exact: true })).toBeVisible();
    await expect(page.getByLabel(/荷受人の確認/)).toBeVisible();
    await page.screenshot({ path: `${OUT}/14-S50-claim.png`, fullPage: true });
  });

  test('14 引取待ち', async ({ page }) => {
    // **本文が「港を選ぶまで一覧は出ません」「通関の状態は分かりません」と
    // 書いている。** 港を選んだ状態で撮らないと、文章と画像が別々に正しくなる。
    await page.route('**/api/v1/handling/voyages', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [{ voyageNumber: 'V-ONE-002', unLocode: 'USNYC', cargoCount: 3 }],
        }),
      }),
    );
    await page.route('**/api/v1/handling/awaiting-claim*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              trackingNumber: 'TRK-AB12CD3456',
              bookingId: 'b-1',
              originUnLocode: 'JPTYO',
              destinationUnLocode: 'USNYC',
              cargoType: 'GENERAL',
              handledTypes: ['UNLOAD'],
            },
            {
              trackingNumber: 'TRK-EF78GH9012',
              bookingId: 'b-2',
              originUnLocode: 'JPOSA',
              destinationUnLocode: 'USNYC',
              cargoType: 'REEFER',
              handledTypes: ['UNLOAD'],
            },
          ],
        }),
      }),
    );
    await signInAsHandler(page);
    await page.goto('/handling/awaiting-claim');
    await expect(page.getByRole('heading', { name: '引取待ち' })).toBeVisible();
    await page.getByLabel('港').selectOption('USNYC');
    await expect(page.getByRole('link', { name: '引取を記録' }).first()).toBeVisible();
    await page.screenshot({ path: `${OUT}/14-S54-awaiting-claim.png`, fullPage: true });
  });

  test('14 例外の起票', async ({ page }) => {
    await signInAsTracker(page);
    await page.goto('/tracking/TRK-AB12CD3456/exceptions/new');
    await expect(page.getByRole('heading', { name: '例外を起票する' })).toBeVisible();
    // **本文が「発生状況に、対応する人が読んで動ける内容を書きます」と書いている。**
    // 空のまま撮ると、何を書く欄なのかが画像から分からない。
    await page.getByLabel('発生場所').fill('SGSIN');
    await page.getByLabel('発生状況').fill('台風で 3 日遅れます。代替便を手配中です');
    await page.screenshot({ path: `${OUT}/14-S43-exception-report.png`, fullPage: true });
  });

  test('14 例外一覧', async ({ page }) => {
    // **本文が「緊急のものから」「解決したものは出ません」と書いている。**
    // 緊急の行が無いと、文章と画像が別々に正しくなる。
    await page.route('**/api/v1/tracking/trackings/exceptions', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              exceptionId: 'ex-1',
              trackingNumber: 'TRK-EF78GH9012',
              exceptionType: 'LOSS',
              exceptionTypeLabel: '紛失',
              responseStatus: 'REPORTED',
              responseStatusLabel: '起票',
              urgent: true,
              unLocode: 'SGSIN',
              description: '積替えの際に見当たらなくなりました',
              occurredAt: '2026-09-22T02:00:00Z',
              estimatedArrival: '2026-09-30T18:00:00Z',
              transportStatus: 'EXCEPTION',
              transportStatusLabel: '例外発生',
            },
            {
              exceptionId: 'ex-2',
              trackingNumber: 'TRK-AB12CD3456',
              exceptionType: 'DELAY',
              exceptionTypeLabel: '遅延',
              responseStatus: 'RESPONDING',
              responseStatusLabel: '対応中',
              urgent: false,
              unLocode: 'SGSIN',
              description: '台風で 3 日遅れます',
              occurredAt: '2026-09-20T02:00:00Z',
              estimatedArrival: '2026-09-27T18:00:00Z',
              transportStatus: 'EXCEPTION',
              transportStatusLabel: '例外発生',
            },
          ],
        }),
      }),
    );
    await signInAsTracker(page);
    await page.goto('/tracking/exceptions');
    await expect(page.getByRole('heading', { name: '未解決の例外' })).toBeVisible();
    await expect(page.getByText('緊急', { exact: true })).toBeVisible();
    await page.screenshot({ path: `${OUT}/14-S42-exception-list.png`, fullPage: true });
  });
});
