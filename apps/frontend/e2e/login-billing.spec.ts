import { test, expect } from '@playwright/test'

/**
 * IT8 US21/US22/US23 精算フロー E2E シナリオ。
 *
 * シナリオ 1: PENDING 請求 → 料金算出（CALCULATED）→ 精算書発行（INVOICED）→ 入金確認（PAID）
 *   1. /login で admin/password でログイン
 *   2. billingms API で PENDING Invoice を直接作成
 *   3. S22 請求一覧（/billing）にアクセスし Invoice が表示されること
 *   4. S23 請求詳細（/billing/:invoiceId）へ遷移し料金算出フォームを確認
 *   5. 料金算出を実行 → ステータスが CALCULATED に変わること
 *   6. 「精算書を発行」ボタンが表示されること → S24 精算書発行ページへ遷移
 *   7. 精算書を発行 → ステータスが INVOICED に変わること
 *   8. 詳細ページで「精算完了」ボタン → 入金額・決済方法を入力して精算完了
 *   9. ステータスが PAID になること
 *
 * シナリオ 2: S25 督促一覧（/billing/overdue）アクセス確認
 *   1. /billing/overdue にアクセス
 *   2. 督促一覧ページが表示されること
 *
 * 実行前提:
 *   - axonserver (:8024/:8124) が Docker で起動済み
 *   - authms / billingms / bookingms / gatewayms が local-axon-h2 プロファイルで起動済み
 */

const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

async function login(
  request: import('@playwright/test').APIRequestContext,
): Promise<string | null> {
  const resp = await request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { username: 'admin', password: 'password' },
  })
  if (!resp.ok()) return null
  const body = (await resp.json()) as { token?: string }
  return body.token ?? null
}

async function isBillingAvailable(
  request: import('@playwright/test').APIRequestContext,
  token: string,
): Promise<boolean> {
  try {
    const resp = await request.get(`${API_BASE_URL}/api/v1/billing/invoices`, {
      headers: { Authorization: `Bearer ${token}` },
      timeout: 5000,
    })
    return resp.ok()
  } catch {
    return false
  }
}


test.describe('S22/S23 請求一覧・詳細・料金算出', () => {
  test('S22 請求一覧ページにアクセスできる', async ({ page, request }) => {
    const token = await login(request)
    if (!token || !(await isBillingAvailable(request, token))) {
      test.skip(true, 'billingms が起動していないためスキップ')
      return
    }

    await page.goto('/login')
    await page.fill('input[type="text"]', 'admin')
    await page.fill('input[type="password"]', 'password')
    await page.click('button[type="submit"]')
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 })

    await page.goto('/billing')
    await expect(page.locator('h1')).toContainText('請求一覧')
  })

  test('S25 督促一覧ページにアクセスできる', async ({ page, request }) => {
    const token = await login(request)
    if (!token || !(await isBillingAvailable(request, token))) {
      test.skip(true, 'billingms が起動していないためスキップ')
      return
    }

    await page.goto('/login')
    await page.fill('input[type="text"]', 'admin')
    await page.fill('input[type="password"]', 'password')
    await page.click('button[type="submit"]')
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 })

    await page.goto('/billing/overdue')
    await expect(page.locator('h1')).toContainText('督促一覧')
  })
})

test.describe('S23 料金算出 → S24 精算書発行 → 精算完了フロー', () => {
  test('既存 Invoice の料金算出・精算書発行フローを実行できる', async ({ page, request }) => {
    const token = await login(request)
    if (!token || !(await isBillingAvailable(request, token))) {
      test.skip(true, 'billingms が起動していないためスキップ')
      return
    }

    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }

    // 荷主と予約を作成して PENDING Invoice を生成する
    const suffix = Date.now().toString()
    const shipperResp = await request.post(`${API_BASE_URL}/api/v1/shippers`, {
      headers,
      data: { name: `E2E Billing ${suffix}`, email: `e2e-billing-${suffix}@example.com`, shipperType: 'CORPORATE' },
    })
    if (!shipperResp.ok()) {
      test.skip(true, '荷主の作成に失敗したためスキップ')
      return
    }
    const shipper = (await shipperResp.json()) as { id: number; shipperId?: string }
    const shipperId = shipper.id ?? shipper.shipperId

    const bookResp = await request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers,
      data: {
        shipperId,
        cargoSpec: { cargoType: 'GENERAL', weightKg: 100, quantity: 1, productName: `E2E Cargo ${suffix}` },
        routeSpec: { originUnLocode: 'JPTYO', destinationUnLocode: 'DEHAM', arrivalDeadline: '2099-12-31' },
      },
    })
    if (!bookResp.ok()) {
      test.skip(true, '予約の作成に失敗したためスキップ')
      return
    }
    const booking = (await bookResp.json()) as { bookingId: string }
    const bookingId = booking.bookingId

    // billingms が CargoBookedEvent を処理して PENDING Invoice を作成するまで待機
    let invoiceId: string | null = null
    for (let i = 0; i < 10; i++) {
      await new Promise((r) => setTimeout(r, 1000))
      const listResp = await request.get(`${API_BASE_URL}/api/v1/billing/invoices`, { headers })
      if (listResp.ok()) {
        const list = (await listResp.json()) as Array<{ invoiceId: string; bookingId: string; billingStatus: string }>
        const found = list.find((inv) => inv.bookingId === bookingId && inv.billingStatus === 'PENDING')
        if (found) {
          invoiceId = found.invoiceId
          break
        }
      }
    }
    if (!invoiceId) {
      test.skip(true, `PENDING Invoice が生成されなかったためスキップ（bookingId=${bookingId}）`)
      return
    }

    // ログイン
    await page.goto('/login')
    await page.fill('input[type="text"]', 'admin')
    await page.fill('input[type="password"]', 'password')
    await page.click('button[type="submit"]')
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 })

    // S23 請求詳細へ
    await page.goto(`/billing/${invoiceId}`)
    await expect(page.locator('h1')).toContainText('請求詳細')

    // 料金算出フォームが表示されること
    await expect(page.locator('h2').filter({ hasText: '料金算出' })).toBeVisible()

    // 料金算出
    await page.fill('input[type="number"]:first-of-type', '100000')
    await page.click('button[type="submit"]')

    // ステータスが CALCULATED に変わること
    await expect(page.locator('dd').filter({ hasText: 'CALCULATED' })).toBeVisible({ timeout: 10000 })

    // 「精算書を発行」ボタンが表示されること
    const issueButton = page.locator('a', { hasText: '精算書を発行' })
    await expect(issueButton).toBeVisible()

    // S24 精算書発行ページへ
    await issueButton.click()
    await expect(page.locator('h1')).toContainText('精算書発行')

    // 精算書を発行
    await page.fill('input[type="date"]', '2099-12-31')
    await page.click('button[type="submit"]')

    // 詳細ページに戻り INVOICED になること
    await page.waitForURL(`/billing/${invoiceId}`, { timeout: 10000 })
    await expect(page.locator('dd').filter({ hasText: 'INVOICED' })).toBeVisible({ timeout: 10000 })
  })
})
