import { test, expect } from '@playwright/test';

// IT1 デモ項目の受け入れ E2E（US02・US03・US04）。
// 一意なメールで冪等性を確保する。
function unique(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

test.describe('US02/US03: 荷主登録', () => {
  test('個人荷主を登録し一覧に表示される（US02）', async ({ page }) => {
    const email = `${unique('taro')}@example.com`;
    await page.goto('/shippers/new');
    await page.getByTestId('shipper-type').selectOption('INDIVIDUAL');
    await page.getByTestId('name').fill('山田太郎');
    await page.getByTestId('email').fill(email);
    await page.getByTestId('address').fill('東京都千代田区');
    await page.getByTestId('submit').click();

    // PRG で一覧へ
    await expect(page).toHaveURL(/\/shippers$/);
    await expect(page.getByText('山田太郎').first()).toBeVisible();
  });

  test('法人荷主を契約番号・割引率付きで登録できる（US03）', async ({ page }) => {
    const email = `${unique('corp')}@example.com`;
    await page.goto('/shippers/new');
    await page.getByTestId('shipper-type').selectOption('CORPORATE');
    await page.getByTestId('name').fill('株式会社サンプル');
    await page.getByTestId('email').fill(email);
    await page.getByTestId('contract-number').fill('CN-0001');
    await page.getByTestId('discount-percent').fill('15');
    await page.getByTestId('submit').click();

    await expect(page).toHaveURL(/\/shippers$/);
    await expect(page.getByText('株式会社サンプル').first()).toBeVisible();
  });
});

test.describe('US04: 貨物予約登録', () => {
  test('既存荷主を参照して貨物予約を登録できる（US04）', async ({ page }) => {
    // 事前に荷主を登録して荷主コードを取得
    const email = `${unique('book')}@example.com`;
    await page.goto('/shippers/new');
    await page.getByTestId('shipper-type').selectOption('INDIVIDUAL');
    await page.getByTestId('name').fill('予約用荷主');
    await page.getByTestId('email').fill(email);
    await page.getByTestId('submit').click();
    await expect(page).toHaveURL(/\/shippers$/);

    // 一覧の先頭行から荷主コードを取得（SHP-XXXXXXXX）
    const code = await page.getByTestId('shipper-row').first().locator('td').first().textContent();
    expect(code).toMatch(/^SHP-/);

    // 貨物予約登録
    await page.goto('/bookings/new');
    await page.getByTestId('shipper-code').fill(code!.trim());
    await page.getByTestId('cargo-type').selectOption('GENERAL');
    await page.getByTestId('weight').fill('1200.5');
    await page.getByTestId('origin').fill('JPTYO');
    await page.getByTestId('destination').fill('DEHAM');
    await page.getByTestId('arrival-deadline').fill('2026-09-01');
    await page.getByTestId('submit').click();

    // PRG で /bookings へ（仮受付として登録される）
    await expect(page).toHaveURL(/\/bookings$/);
  });
});
