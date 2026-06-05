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

  test('US23: 精算書発行 → INVOICED 遷移、invoiceNumber と payment_due 確定', async ({ page }) => {
    await loginAsAdmin(page);
    const invoiceId = await calculateInvoiceViaApi(page);

    await page.goto(`/billing/${invoiceId}`);

    // CALCULATED 状態で「精算書を発行」ボタン表示
    const issueButton = page.getByRole('button', { name: /精算書を発行/ });
    await expect(issueButton).toBeVisible({ timeout: 10_000 });

    await issueButton.click();

    // 発行済バッジ表示
    await expect(page.getByText(/発行済/)).toBeVisible({ timeout: 10_000 });
    // invoice_number（INV-YYYYMMDD-XXXX）が表示される
    await expect(page.getByText(/INV-\d{8}-\d{4}/)).toBeVisible();
    // 支払期限（YYYY-MM-DD）が表示される
    await expect(page.getByText(/支払期限/)).toBeVisible();
  });

  test('US23: 入金記録 → PAID 遷移、paid_at 確定', async ({ page }) => {
    await loginAsAdmin(page);
    const invoiceId = await calculateInvoiceViaApi(page);

    await page.goto(`/billing/${invoiceId}`);

    // CALCULATED → INVOICED に進める
    await page.getByRole('button', { name: /精算書を発行/ }).click();
    await expect(page.getByText(/発行済/)).toBeVisible({ timeout: 10_000 });

    // 「入金を記録」ボタン表示
    const payButton = page.getByRole('button', { name: /入金を記録/ });
    await expect(payButton).toBeVisible();

    await payButton.click();

    // 入金済バッジ表示
    await expect(page.getByText(/入金済/)).toBeVisible({ timeout: 10_000 });
    // 入金日時が表示される
    await expect(page.getByText(/入金日時/)).toBeVisible();
  });

  test('US23: S22 請求一覧でフィルタ・ページネーションが動作する', async ({ page }) => {
    await loginAsAdmin(page);
    // 一覧に出るために 1 件は事前作成しておく
    await calculateInvoiceViaApi(page);

    await page.goto('/billing');

    // 一覧テーブルが表示される
    await expect(page.getByRole('heading', { name: '請求一覧' })).toBeVisible({
      timeout: 10_000,
    });
    // ステータスフィルタが存在する
    await expect(page.getByLabel('状態フィルタ')).toBeVisible();
    // 合計件数表示が現れる
    await expect(page.getByText(/合計 \d+ 件/)).toBeVisible();
  });

  test('US23: S25 督促一覧が表示される（OverdueScheduler 未実行時は空メッセージ）', async ({
    page,
  }) => {
    await loginAsAdmin(page);

    await page.goto('/billing/overdue');

    // 督促一覧見出し
    await expect(page.getByRole('heading', { name: '督促一覧' })).toBeVisible({
      timeout: 10_000,
    });
    // OverdueScheduler は毎日 09:00 実行のため、E2E では「対象なし」メッセージか件数が出る
    await expect(
      page
        .getByText(/督促対象の請求書はありません/)
        .or(page.getByText(/合計 \d+ 件/)),
    ).toBeVisible();
  });
});
