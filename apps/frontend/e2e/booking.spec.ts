import { test, expect, Page } from '@playwright/test';

/**
 * IT2: 荷主登録 (US02 / US03) と貨物予約登録 (US04 / US05) の
 * フロントエンド E2E シナリオ。
 *
 * 実行前提:
 *   - authms (:8081)、bookingms (:8082)、gatewayms (:8080) が起動済み
 *   - admin ユーザー（ROLE_ADMIN）が DB に存在
 *   - bookingms は local-h2 プロファイル等で各テストごとにクリーン状態が望ましい
 */

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('password');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

/**
 * Read Model（Projection）反映待ち。
 *
 * Axon @EventHandler が cargo_summary / shipper を非同期で更新するため、
 * 登録直後の最初の一覧取得には間に合わないことがある。
 * 指定パスにリロードしながら期待テキストが見えるまで待つ。
 */
async function waitForListEntry(page: Page, path: string, expectedText: string, maxAttempts = 6) {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (await page.getByText(expectedText).first().isVisible().catch(() => false)) {
      return;
    }
    await page.waitForTimeout(500);
    await page.goto(path);
  }
  await expect(page.getByText(expectedText).first()).toBeVisible();
}

async function registerShipper(
  page: Page,
  options: {
    type: 'INDIVIDUAL' | 'CORPORATE';
    name: string;
    email: string;
    contractNumber?: string;
    discountRatePercent?: string;
  }
) {
  await page.goto('/shippers/new');
  await page.locator('#shipperType').selectOption(options.type);
  await page.locator('#name').fill(options.name);
  await page.locator('#addressLine1').fill('東京都千代田区丸の内 1-1-1');
  await page.locator('#city').fill('千代田区');
  await page.locator('#countryCode').fill('JP');
  await page.locator('#email').fill(options.email);
  await page.locator('#phone').fill('03-1234-5678');
  if (options.type === 'CORPORATE') {
    await page.locator('#contractNumber').fill(options.contractNumber ?? 'CT-E2E');
    await page.locator('#discountRatePercent').fill(options.discountRatePercent ?? '10');
  }
  await page.getByRole('button', { name: '登録' }).click();
  await expect(page).toHaveURL('/shippers', { timeout: 10_000 });
}

test.describe('IT2: ページネーション E2E', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('荷主一覧にページネーションが表示される', async ({ page }) => {
    await page.goto('/shippers');
    await expect(page.getByRole('navigation', { name: 'ページネーション' })).toBeVisible();
    await expect(page.getByRole('button', { name: '前へ' })).toBeVisible();
    await expect(page.getByRole('button', { name: '次へ' })).toBeVisible();
  });

  test('予約一覧にページネーションが表示される', async ({ page }) => {
    await page.goto('/bookings');
    await expect(page.getByRole('navigation', { name: 'ページネーション' })).toBeVisible();
    await expect(page.getByRole('button', { name: '前へ' })).toBeVisible();
    await expect(page.getByRole('button', { name: '次へ' })).toBeVisible();
  });

  test('荷主登録後にページネーションの件数表示が更新される', async ({ page }) => {
    const before = await page.goto('/shippers').then(async () => {
      const text = await page.getByRole('navigation', { name: 'ページネーション' })
        .getByRole('status').textContent();
      return text ?? '';
    });
    const beforeCount = parseInt(before.match(/\/\s*(\d+)\s*件/)?.[1] ?? '0', 10);

    const name = `件数確認-${Date.now()}`;
    const email = `e2e-count-${Date.now()}@example.com`;
    await registerShipper(page, { type: 'INDIVIDUAL', name, email });
    await waitForListEntry(page, '/shippers', name);

    const after = await page.getByRole('navigation', { name: 'ページネーション' })
      .getByRole('status').textContent();
    const afterCount = parseInt(after?.match(/\/\s*(\d+)\s*件/)?.[1] ?? '0', 10);
    expect(afterCount).toBeGreaterThan(beforeCount);
  });
});

test.describe('IT2: ナビゲーション E2E', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('ナビゲーションに荷主管理メニューが表示される', async ({ page }) => {
    await expect(page.getByRole('link', { name: '荷主管理' })).toBeVisible();
  });

  test('ナビゲーションに予約管理メニューが表示される', async ({ page }) => {
    await expect(page.getByRole('link', { name: '予約管理' })).toBeVisible();
  });

  test('荷主管理リンククリックで /shippers に遷移する', async ({ page }) => {
    await page.getByRole('link', { name: '荷主管理' }).click();
    await expect(page).toHaveURL('/shippers', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: '荷主一覧' })).toBeVisible();
  });

  test('予約管理リンククリックで /bookings に遷移する', async ({ page }) => {
    await page.getByRole('link', { name: '予約管理' }).click();
    await expect(page).toHaveURL('/bookings', { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: '予約一覧' })).toBeVisible();
  });
});

test.describe('US02/US03: 荷主登録 E2E', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('US02: 個人荷主を登録できる', async ({ page }) => {
    const name = `個人E2E-${Date.now()}`;
    const email = `e2e-individual-${Date.now()}@example.com`;

    await registerShipper(page, { type: 'INDIVIDUAL', name, email });
    await waitForListEntry(page, '/shippers', name);

    await expect(page.getByText(name)).toBeVisible();
    await expect(page.getByText(email)).toBeVisible();
    await expect(
      page.getByRole('row', { name: new RegExp(name) }).getByText('個人', { exact: true })
    ).toBeVisible();
  });

  test('US03: 法人荷主を割引率付きで登録できる', async ({ page }) => {
    const name = `法人E2E-${Date.now()}`;
    const email = `e2e-corp-${Date.now()}@example.com`;

    await registerShipper(page, {
      type: 'CORPORATE',
      name,
      email,
      contractNumber: `CT-${Date.now()}`,
      discountRatePercent: '15',
    });
    await waitForListEntry(page, '/shippers', name);

    const row = page.getByRole('row', { name: new RegExp(name) });
    await expect(row.getByText('法人', { exact: true })).toBeVisible();
    await expect(row.getByText('15.0%')).toBeVisible();
  });

  test('US03: 割引率 30% 超ではエラーが表示される', async ({ page }) => {
    await page.goto('/shippers/new');
    await page.locator('#shipperType').selectOption('CORPORATE');
    await page.locator('#name').fill('割引率超過テスト');
    await page.locator('#addressLine1').fill('東京都港区');
    await page.locator('#city').fill('港区');
    await page.locator('#countryCode').fill('JP');
    await page.locator('#email').fill(`e2e-overrate-${Date.now()}@example.com`);
    await page.locator('#phone').fill('03-0000-0000');
    await page.locator('#contractNumber').fill('CT-OVER');
    await page.locator('#discountRatePercent').fill('31');
    await page.getByRole('button', { name: '登録' }).click();

    await expect(page.getByRole('alert')).toContainText('割引率');
    await expect(page).toHaveURL(/\/shippers\/new$/);
  });
});

test.describe('US04: 一般貨物予約登録 E2E', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('US04: 荷主選択→一般貨物予約登録→一覧反映', async ({ page }) => {
    const shipperName = `予約用荷主-${Date.now()}`;
    const shipperEmail = `e2e-booking-${Date.now()}@example.com`;
    const productName = `E2E一般貨物-${Date.now()}`;
    await registerShipper(page, { type: 'INDIVIDUAL', name: shipperName, email: shipperEmail });
    await waitForListEntry(page, '/shippers', shipperName);

    await page.goto('/bookings/new');
    await expect(page.getByRole('heading', { name: '貨物予約新規登録' })).toBeVisible();

    const shipperOption = page.getByRole('option', { name: new RegExp(shipperName) });
    const shipperValue = await shipperOption.getAttribute('value');
    if (!shipperValue) throw new Error('shipperValue が取得できません');
    await page.locator('#shipperId').selectOption(shipperValue);

    await page.locator('#cargoType').selectOption('GENERAL');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destinationUnlocode').fill('USNYC');
    await page.locator('#arrivalDeadline').fill('2027-12-31');
    await page.locator('#weightKg').fill('1500');
    await page.locator('#quantity').fill('10');
    await page.locator('#productName').fill(productName);
    await page.getByRole('button', { name: '登録' }).click();

    await expect(page).toHaveURL('/bookings', { timeout: 10_000 });
    await waitForListEntry(page, '/bookings', productName);

    const row = page.getByRole('row', { name: new RegExp(productName) });
    await expect(row).toBeVisible();
    await expect(row.getByText('仮受付')).toBeVisible();
  });

  test('US04: 必須項目未入力でエラーが表示される', async ({ page }) => {
    await page.goto('/bookings/new');
    await page.getByRole('button', { name: '登録' }).click();
    await expect(page.getByRole('alert')).toContainText('必須項目');
    await expect(page).toHaveURL(/\/bookings\/new$/);
  });
});

test.describe('US05: 危険物・冷凍貨物予約登録 E2E', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('US05: 危険物選択で危険物申告フィールドが表示される', async ({ page }) => {
    await page.goto('/bookings/new');
    await expect(page.getByLabel('IMO 分類クラス')).toHaveCount(0);

    await page.locator('#cargoType').selectOption('HAZARDOUS');

    await expect(page.getByLabel('IMO 分類クラス')).toBeVisible();
    await expect(page.getByLabel('国連番号')).toBeVisible();
    await expect(page.getByLabel('申告文')).toBeVisible();
  });

  test('US05: 冷凍選択で温度フィールドが表示される', async ({ page }) => {
    await page.goto('/bookings/new');
    await expect(page.getByLabel('最低温度 (℃)')).toHaveCount(0);

    await page.locator('#cargoType').selectOption('REFRIGERATED');

    await expect(page.getByLabel('最低温度 (℃)')).toBeVisible();
    await expect(page.getByLabel('最高温度 (℃)')).toBeVisible();
  });

  test('US05: 危険物予約を登録できる', async ({ page }) => {
    const shipperName = `危険物荷主-${Date.now()}`;
    const shipperEmail = `e2e-hazard-${Date.now()}@example.com`;
    const productName = `アセトン-E2E-${Date.now()}`;
    await registerShipper(page, { type: 'INDIVIDUAL', name: shipperName, email: shipperEmail });
    await waitForListEntry(page, '/shippers', shipperName);

    await page.goto('/bookings/new');
    const shipperOption = page.getByRole('option', { name: new RegExp(shipperName) });
    const shipperValue = await shipperOption.getAttribute('value');
    if (!shipperValue) throw new Error('shipperValue が取得できません');
    await page.locator('#shipperId').selectOption(shipperValue);

    await page.locator('#cargoType').selectOption('HAZARDOUS');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destinationUnlocode').fill('USNYC');
    await page.locator('#arrivalDeadline').fill('2027-12-31');
    await page.locator('#weightKg').fill('1500');
    await page.locator('#quantity').fill('10');
    await page.locator('#productName').fill(productName);
    await page.locator('#hazardImoClass').fill('3');
    await page.locator('#hazardUnNumber').fill('UN1090');
    await page.locator('#hazardDeclaration').fill('引火性液体・直射日光厳禁');

    await page.getByRole('button', { name: '登録' }).click();

    await expect(page).toHaveURL('/bookings', { timeout: 10_000 });
    await waitForListEntry(page, '/bookings', productName);
    await expect(
      page.getByRole('row', { name: new RegExp(productName) }).getByText('危険物', { exact: true })
    ).toBeVisible();
  });

  test('US05: 危険物選択で hazard フィールド未入力ならエラー', async ({ page }) => {
    const shipperName = `危険物未入力-${Date.now()}`;
    const shipperEmail = `e2e-hazard-missing-${Date.now()}@example.com`;
    await registerShipper(page, { type: 'INDIVIDUAL', name: shipperName, email: shipperEmail });

    await page.goto('/bookings/new');
    const shipperOption = page.getByRole('option', { name: new RegExp(shipperName) });
    const shipperValue = await shipperOption.getAttribute('value');
    if (!shipperValue) throw new Error('shipperValue が取得できません');
    await page.locator('#shipperId').selectOption(shipperValue);

    await page.locator('#cargoType').selectOption('HAZARDOUS');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destinationUnlocode').fill('USNYC');
    await page.locator('#arrivalDeadline').fill('2027-12-31');
    await page.locator('#weightKg').fill('1500');
    await page.locator('#quantity').fill('10');
    await page.locator('#productName').fill('未申告');

    await page.getByRole('button', { name: '登録' }).click();

    await expect(page.getByRole('alert')).toContainText('危険物');
    await expect(page).toHaveURL(/\/bookings\/new$/);
  });

  test('US05: 冷凍予約を登録できる', async ({ page }) => {
    const shipperName = `冷凍荷主-${Date.now()}`;
    const shipperEmail = `e2e-refrig-${Date.now()}@example.com`;
    const productName = `冷凍マグロ-E2E-${Date.now()}`;
    await registerShipper(page, { type: 'INDIVIDUAL', name: shipperName, email: shipperEmail });
    await waitForListEntry(page, '/shippers', shipperName);

    await page.goto('/bookings/new');
    const shipperOption = page.getByRole('option', { name: new RegExp(shipperName) });
    const shipperValue = await shipperOption.getAttribute('value');
    if (!shipperValue) throw new Error('shipperValue が取得できません');
    await page.locator('#shipperId').selectOption(shipperValue);

    await page.locator('#cargoType').selectOption('REFRIGERATED');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destinationUnlocode').fill('USNYC');
    await page.locator('#arrivalDeadline').fill('2027-12-31');
    await page.locator('#weightKg').fill('2000');
    await page.locator('#quantity').fill('20');
    await page.locator('#productName').fill(productName);
    await page.locator('#temperatureMinC').fill('-25');
    await page.locator('#temperatureMaxC').fill('-18');

    await page.getByRole('button', { name: '登録' }).click();

    await expect(page).toHaveURL('/bookings', { timeout: 10_000 });
    await waitForListEntry(page, '/bookings', productName);
    await expect(
      page.getByRole('row', { name: new RegExp(productName) }).getByText('冷凍', { exact: true })
    ).toBeVisible();
  });

  test('US05: 冷凍選択で min > max ならエラー', async ({ page }) => {
    const shipperName = `温度逆転荷主-${Date.now()}`;
    const shipperEmail = `e2e-tempinv-${Date.now()}@example.com`;
    await registerShipper(page, { type: 'INDIVIDUAL', name: shipperName, email: shipperEmail });
    await waitForListEntry(page, '/shippers', shipperName);

    await page.goto('/bookings/new');
    const shipperOption = page.getByRole('option', { name: new RegExp(shipperName) });
    const shipperValue = await shipperOption.getAttribute('value');
    if (!shipperValue) throw new Error('shipperValue が取得できません');
    await page.locator('#shipperId').selectOption(shipperValue);

    await page.locator('#cargoType').selectOption('REFRIGERATED');
    await page.locator('#originUnlocode').fill('JPTYO');
    await page.locator('#destinationUnlocode').fill('USNYC');
    await page.locator('#arrivalDeadline').fill('2027-12-31');
    await page.locator('#weightKg').fill('2000');
    await page.locator('#quantity').fill('20');
    await page.locator('#productName').fill('温度逆転');
    await page.locator('#temperatureMinC').fill('-10');
    await page.locator('#temperatureMaxC').fill('-25');
    await page.getByRole('button', { name: '登録' }).click();

    await expect(page.getByRole('alert')).toContainText('最低温度は最高温度以下');
    await expect(page).toHaveURL(/\/bookings\/new$/);
  });
});
