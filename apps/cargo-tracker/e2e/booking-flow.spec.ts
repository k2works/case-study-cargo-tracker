import { test, expect, type Page } from '@playwright/test';

/**
 * IT2 デモ項目 E2E（実ブラウザ）: 見積 → 予約 → 引き渡しの縦フロー。
 * デフォルトシードの荷主が無いため、まず荷主登録してからそのコードで予約する。
 */

async function loginAsSales(page: Page): Promise<void> {
  await page.goto('/login');
  await page.fill('#username', 'sales');
  await page.fill('#password', 'password');
  await page.click('[data-testid="login-submit"]');
  await expect(page.getByTestId('dashboard-heading')).toBeVisible();
}

function futureDate(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

test('営業担当者が見積を作成しルート候補を確認できる', async ({ page }) => {
  await loginAsSales(page);
  await page.click('[data-testid="nav-/estimates"]');
  await page.click('[data-testid="estimate-new-link"]');
  await page.fill('#origin', 'JPTYO');
  await page.fill('#destination', 'USLAX');
  await page.fill('#arrivalDeadline', futureDate(60));
  await page.fill('#weightKg', '1000');
  await page.click('[data-testid="estimate-submit"]');
  await expect(page.getByTestId('estimate-number')).toBeVisible();
  await expect(page.getByTestId('route-candidates')).toContainText('直行');
});

test('荷主登録 → 予約登録 → 経路設計者へ引き渡しの縦フロー', async ({ page }) => {
  await loginAsSales(page);

  // 1. 荷主登録して荷主 ID を得る
  await page.goto('/shippers/new');
  const uniqueEmail = `flow-${futureDate(1)}-${Math.floor(performance.now())}@example.com`;
  await page.fill('#name', 'フロー荷主');
  await page.fill('#email', uniqueEmail);
  await page.click('[data-testid="shipper-submit"]');
  await expect(page.getByTestId('dashboard-flash')).toContainText('荷主 ID:');
  const flashText = await page.getByTestId('dashboard-flash').innerText();
  const shipperCode = flashText.match(/SHP-[0-9a-f]{8}/)?.[0];
  expect(shipperCode).toBeTruthy();

  // 2. 予約登録
  await page.goto('/bookings/new');
  await page.fill('#shipperCode', shipperCode!);
  await page.fill('#consigneeName', '受取花子');
  await page.fill('#consigneeEmail', 'uke@example.com');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'SGSIN');
  await page.fill('#arrivalDeadline', futureDate(45));
  await page.fill('#weightKg', '800');
  await page.click('[data-testid="booking-submit"]');
  await expect(page.getByTestId('booking-status')).toContainText('仮受付');

  // 3. 経路設計者へ引き渡し
  await page.click('[data-testid="assign-to-routing"]');
  await expect(page.getByTestId('booking-status')).toContainText('経路設計中');
});

test('予約フォームで危険物を選ぶと申告フィールドが htmx で表示される', async ({ page }) => {
  await loginAsSales(page);
  await page.goto('/bookings/new');
  await expect(page.locator('#hazardousClass')).toHaveCount(0);
  await page.selectOption('#cargoType', 'HAZARDOUS');
  await expect(page.locator('#hazardousClass')).toBeVisible();
  await expect(page.locator('#unNumber')).toBeVisible();
});
