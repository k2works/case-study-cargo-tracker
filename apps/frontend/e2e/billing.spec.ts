import { test, expect, Page } from '@playwright/test';

/**
 * IT7 2.7: US21 輸送料金算出 UI の E2E（S23 請求詳細・算出画面）。
 *
 * Kafka は不要。authms + billingms + gatewayms の起動を前提とする。
 * 完全な「DELIVERED → InvoiceCalculated → ... → SETTLED」貫通シナリオは
 * cross-service.spec.ts に IT7 Task 5.1 で追加する。本 spec は UI/UX の振る舞いを担保する。
 *
 * 実行前提:
 *   - authms (:8081)、billingms (:8086)、gatewayms (:8080) が起動済み
 *   - admin ユーザー（ROLE_ADMIN）が DB に存在
 *
 * 注: S22 請求一覧 / S24 精算書発行 / S25 督促一覧 のフロント実装は IT7 Task 4.7 で
 * 追加する。本 spec はまず S23 単独のシナリオに集中する。
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('password');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

/**
 * POST /api/v1/billing/invoices で新規 Invoice を作成して invoiceId を返す。
 * gatewayms (8080) を経由する。
 */
async function calculateInvoiceViaApi(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('auth_token'));
  const response = await page.request.post('/api/v1/billing/invoices', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data: {
      bookingId: `B-E2E-${Date.now()}`,
      shipperId: 'S-E2E-001',
      distanceKm: '5300',
      weightKg: '1200',
      cargoType: 'GENERAL',
      handlingCount: 8,
      currency: 'JPY',
    },
  });
  expect(response.status()).toBe(202);
  const body = await response.json();
  return body.invoiceId;
}

test.describe('IT7 / S23: 請求詳細・算出 UI', () => {
  test('US21: POST /invoices で算出 → S23 で内訳と合計（330,000 円）を表示', async ({ page }) => {
    await loginAsAdmin(page);

    const invoiceId = await calculateInvoiceViaApi(page);

    await page.goto(`/billing/${invoiceId}`);

    // S20 UI サンプル値: 1200kg × 5300km × 0.05 + 8 × 1500 = 330,000 円
    await expect(page.getByRole('heading', { name: new RegExp(invoiceId) })).toBeVisible({
      timeout: 10_000,
    });
    // Read Model 投影は非同期。waitFor で 330,000 円が表示されるまで待つ
    await expect(page.getByText(/330,000/).first()).toBeVisible({ timeout: 10_000 });
    // 状態は CALCULATED（日本語: 算出済）
    await expect(page.getByText(/算出済/)).toBeVisible();
    // 料金内訳に「基本料金」行が存在
    await expect(page.getByText(/基本料金/).first()).toBeVisible();
  });

  test('US21: 存在しない invoiceId にアクセスすると 404 エラーメッセージを表示', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/billing/INV-NONEXISTENT-0000');

    await expect(page.getByText(/請求書が見つかりません/)).toBeVisible({ timeout: 10_000 });
  });

  test('US22: 割引を適用ボタン押下で StubShipperInfoAcl の 15% 割引が反映される', async ({ page }) => {
    await loginAsAdmin(page);

    const invoiceId = await calculateInvoiceViaApi(page);

    await page.goto(`/billing/${invoiceId}`);

    // 算出済（割引未適用）状態で「割引を適用」ボタンが表示される
    const button = page.getByRole('button', { name: /割引を適用/ });
    await expect(button).toBeVisible({ timeout: 10_000 });

    // クリックで API 呼出 → 再フェッチ
    await button.click();

    // 割引適用済バッジが表示される（StubShipperInfoAcl は CORPORATE 15%）
    await expect(page.getByText(/割引適用済/)).toBeVisible({ timeout: 10_000 });
    // 割引前後対比セクションが現れる
    await expect(page.getByText(/割引前後の対比/)).toBeVisible();
    // 330,000 × 0.15 = 49,500 円の割引額
    await expect(page.getByText(/-49,500/).first()).toBeVisible();
    // 割引後 totalAmount = 280,500 円
    await expect(page.getByText(/280,500/).first()).toBeVisible();
  });
});
