import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers';

// IT2 デモ項目の受け入れ E2E（US05 特殊貨物・US13 予約確定/キャンセル）。
function unique(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

// 荷主を登録し荷主コード（SHP-XXXXXXXX）を返す。
async function registerShipper(page: Page): Promise<string> {
  const email = `${unique('it2')}@example.com`;
  await page.goto('/shippers/new');
  await page.getByTestId('shipper-type').selectOption('INDIVIDUAL');
  await page.getByTestId('name').fill('IT2 荷主');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/shippers$/);
  const code = await page.getByTestId('shipper-row').first().locator('td').first().textContent();
  expect(code).toMatch(/^SHP-/);
  return code!.trim();
}

test.beforeEach(async ({ page }) => {
  await login(page, USERS.sales);
});

test.describe('US05: 危険物・冷凍貨物の予約登録', () => {
  test('危険物予約を危険物申告付きで登録できる', async ({ page }) => {
    const code = await registerShipper(page);
    await page.goto('/bookings/new');
    await page.getByTestId('cargo-type').selectOption('HAZARDOUS');
    // 貨物種別選択で危険物申告フィールドが表示される
    await expect(page.getByTestId('hazardous-fields')).toBeVisible();
    await expect(page.getByTestId('refrigerated-fields')).toBeHidden();

    await page.getByTestId('shipper-code').fill(code);
    await page.getByTestId('weight').fill('500');
    await page.getByTestId('origin').fill('JPTYO');
    await page.getByTestId('destination').fill('DEHAM');
    await page.getByTestId('hazard-class').fill('3');
    await page.getByTestId('un-number').fill('UN1203');
    await page.getByTestId('proper-shipping-name').fill('Gasoline');
    await page.getByTestId('submit').click();

    await expect(page).toHaveURL(/\/bookings\/confirm\/BKG-/);
    await expect(page.getByTestId('booking-status')).toContainText('仮受付');
  });

  test('冷凍貨物予約を温度条件付きで登録できる', async ({ page }) => {
    const code = await registerShipper(page);
    await page.goto('/bookings/new');
    await page.getByTestId('cargo-type').selectOption('REFRIGERATED');
    await expect(page.getByTestId('refrigerated-fields')).toBeVisible();
    await expect(page.getByTestId('hazardous-fields')).toBeHidden();

    await page.getByTestId('shipper-code').fill(code);
    await page.getByTestId('weight').fill('800');
    await page.getByTestId('origin').fill('JPTYO');
    await page.getByTestId('destination').fill('DEHAM');
    await page.getByTestId('min-temperature').fill('-20');
    await page.getByTestId('max-temperature').fill('-5');
    await page.getByTestId('temperature-unit').selectOption('CELSIUS');
    await page.getByTestId('submit').click();

    await expect(page).toHaveURL(/\/bookings\/confirm\/BKG-/);
  });

  test('温度範囲が逆転しているとエラーになる（US05 異常系）', async ({ page }) => {
    const code = await registerShipper(page);
    await page.goto('/bookings/new');
    await page.getByTestId('cargo-type').selectOption('REFRIGERATED');
    await page.getByTestId('shipper-code').fill(code);
    await page.getByTestId('weight').fill('800');
    await page.getByTestId('origin').fill('JPTYO');
    await page.getByTestId('destination').fill('DEHAM');
    await page.getByTestId('min-temperature').fill('5');
    await page.getByTestId('max-temperature').fill('-5');
    await page.getByTestId('temperature-unit').selectOption('CELSIUS');
    await page.getByTestId('submit').click();

    await expect(page.getByTestId('flash-error')).toBeVisible();
  });
});

test.describe('US13: 予約確定・キャンセル', () => {
  // 一般貨物を登録し予約詳細へ遷移するヘルパー。
  async function createAndOpenDetail(page: Page): Promise<void> {
    const code = await registerShipper(page);
    await page.goto('/bookings/new');
    await page.getByTestId('shipper-code').fill(code);
    await page.getByTestId('cargo-type').selectOption('GENERAL');
    await page.getByTestId('weight').fill('300');
    await page.getByTestId('origin').fill('JPTYO');
    await page.getByTestId('destination').fill('DEHAM');
    await page.getByTestId('submit').click();
    await expect(page).toHaveURL(/\/bookings\/confirm\/BKG-/);
    await page.getByTestId('to-detail').click();
    await expect(page).toHaveURL(/\/bookings\/BKG-/);
  }

  test('仮受付の予約を確定できる', async ({ page }) => {
    await createAndOpenDetail(page);
    await page.getByTestId('confirm-booking').click();
    await expect(page.getByTestId('booking-status')).toContainText('予約確定');
  });

  test('予約をキャンセルできる', async ({ page }) => {
    await createAndOpenDetail(page);
    await page.getByTestId('cancel-booking').click();
    await expect(page.getByTestId('booking-status')).toContainText('キャンセル');
  });

  test('キャンセル済み予約では確定・差し戻し・キャンセルの操作ボタンが出ない（許容外遷移の抑止）', async ({ page }) => {
    await createAndOpenDetail(page);
    await page.getByTestId('cancel-booking').click();
    await expect(page.getByTestId('booking-status')).toContainText('キャンセル');
    // ドメインの許容状態に合わせ、キャンセル済みでは全アクションが非表示
    await expect(page.getByTestId('confirm-booking')).toHaveCount(0);
    await expect(page.getByTestId('send-back-booking')).toHaveCount(0);
    await expect(page.getByTestId('cancel-booking')).toHaveCount(0);
  });

  test('確定後に経路再設計へ差し戻せる', async ({ page }) => {
    await createAndOpenDetail(page);
    await page.getByTestId('confirm-booking').click();
    await expect(page.getByTestId('booking-status')).toContainText('予約確定');
    await page.getByTestId('send-back-booking').click();
    await expect(page.getByTestId('booking-status')).toContainText('仮受付');
  });
});
