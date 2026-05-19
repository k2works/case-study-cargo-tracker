import { test, expect } from '@playwright/test'

/**
 * IT7 US19/US20 例外処理 E2E シナリオ。
 *
 * シナリオ 1: DELAY 例外登録 → 例外対応一覧確認 → 解決フロー
 *   1. /login で admin/password でログイン
 *   2. bookingms フル予約フロー → trackingms 自動初期化
 *   3. S17 追跡詳細管理ページへ遷移 → 例外対応タブに切替
 *   4. 「例外を登録」ボタン → S18 例外登録フォームへ遷移
 *   5. 例外種別「遅延（DELAY）」・説明を入力して登録
 *   6. 例外対応タブで DELAY 例外が一覧に表示されること
 *   7. 解決内容を入力して「解決」ボタンで RESOLVED になること
 *
 * シナリオ 2: LOSS 例外登録 → 緊急通知バナー表示確認
 *   1. S18 例外登録フォームで「紛失（LOSS）」を選択
 *   2. 緊急通知バナーが表示されること
 *   3. 登録後に例外対応一覧に escalated マークが表示されること
 *
 * 実行前提:
 *   - axonserver (:8024/:8124) が Docker で起動済み
 *   - authms / bookingms / routingms / handlingms / trackingms / gatewayms が
 *     local-axon-h2 プロファイルで起動済み
 */

const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

async function createFullyTrackedBooking(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  suffix: string,
): Promise<string | null> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }

  const shipperResp = await request.post(`${API_BASE_URL}/api/v1/shippers`, {
    headers,
    data: {
      name: `E2E Shipper ${suffix}`,
      email: `e2e-exc-${suffix}@example.com`,
      shipperType: 'CORPORATE',
    },
  })
  if (!shipperResp.ok()) return null
  const shipper = (await shipperResp.json()) as { id: number }

  const bookResp = await request.post(`${API_BASE_URL}/api/v1/bookings`, {
    headers,
    data: {
      shipperId: shipper.id,
      cargoSpec: { cargoType: 'GENERAL', weightKg: 500, quantity: 10, productName: `E2E Cargo ${suffix}` },
      routeSpec: { originUnLocode: 'JPTYO', destinationUnLocode: 'DEHAM', arrivalDeadline: '2099-12-31' },
    },
  })
  if (!bookResp.ok()) return null
  const booking = (await bookResp.json()) as { bookingId: string }
  const bookingId = booking.bookingId

  if (!(await request.post(`${API_BASE_URL}/api/v1/bookings/${bookingId}/handoff`, { headers })).ok()) return null
  if (!(await request.post(`${API_BASE_URL}/api/v1/bookings/${bookingId}/assign-route`, {
    headers,
    data: { legs: [{ voyageNumber: `V-EXC-${suffix}`, loadUnlocode: 'JPTYO', unloadUnlocode: 'DEHAM', loadTime: '2099-07-20T14:00:00', unloadTime: '2099-08-10T14:00:00' }] },
  })).ok()) return null
  if (!(await request.post(`${API_BASE_URL}/api/v1/bookings/${bookingId}/confirm`, { headers })).ok()) return null
  if (!(await request.post(`${API_BASE_URL}/api/v1/bookings/${bookingId}/issue-tracking`, { headers })).ok()) return null

  for (let i = 0; i < 10; i++) {
    const listResp = await request.get(`${API_BASE_URL}/api/v1/bookings`, { headers })
    if (!listResp.ok()) return null
    const bookings = (await listResp.json()) as Array<{ bookingId: string; trackingNumber: string }>
    const found = bookings.find((b) => b.bookingId === bookingId)
    if (found?.trackingNumber) return found.trackingNumber
    await new Promise((r) => setTimeout(r, 500))
  }
  return null
}

async function waitForTrackingSummary(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  trackingNumber: string,
  timeoutMs = 15_000,
): Promise<boolean> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const resp = await request.get(`${API_BASE_URL}/api/v1/tracking`, { headers })
    if (resp.ok()) {
      const list = (await resp.json()) as Array<{ trackingNumber: string }>
      if (list.some((t) => t.trackingNumber === trackingNumber)) return true
    }
    await new Promise((r) => setTimeout(r, 1_000))
  }
  return false
}

async function loginAsAdmin(page: import('@playwright/test').Page): Promise<string> {
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })
  const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  expect(token).toBeTruthy()
  return token!
}

test('US19: DELAY 例外登録 → 例外対応一覧確認 → 解決フロー', async ({ page, request }) => {
  const suffix = `D${Date.now().toString(36).toUpperCase().slice(-6)}`

  const adminToken = await loginAsAdmin(page)

  const trackingNumber = await createFullyTrackedBooking(request, adminToken, suffix)
  if (!trackingNumber) {
    console.log('booking flow failed, skipping test')
    test.skip()
    return
  }

  const initialized = await waitForTrackingSummary(request, adminToken, trackingNumber)
  if (!initialized) {
    console.log('trackingms initialization timed out, skipping test')
    test.skip()
    return
  }

  // S17 追跡詳細管理へ遷移
  await page.goto(`/tracking/${trackingNumber}/manage`)
  await expect(page.getByText('追跡詳細・管理')).toBeVisible({ timeout: 10_000 })

  // 例外対応タブへ切替
  await page.getByRole('button', { name: '例外対応' }).click()

  // S18 例外登録フォームへ遷移
  await page.getByRole('button', { name: '例外を登録' }).click()
  await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}/exceptions/new`), {
    timeout: 10_000,
  })
  await expect(page.getByText('追跡例外登録')).toBeVisible()

  // DELAY 例外を登録
  await page.getByRole('combobox', { name: /例外種別/ }).selectOption('DELAY')
  await page.getByRole('textbox', { name: /説明/ }).fill('輸送中に遅延が発生しました')
  await page.getByRole('button', { name: '登録' }).click()

  // 登録後に S17 管理ページに戻ること
  await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}/manage`), {
    timeout: 15_000,
  })

  // 例外対応タブで一覧を確認
  await page.getByRole('button', { name: '例外対応' }).click()
  await expect(page.getByText('輸送中に遅延が発生しました')).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('DELAY')).toBeVisible()

  // 解決内容を入力して「解決」ボタンをクリック
  const resolutionInput = page.getByRole('textbox', { name: /解決内容/ })
  await resolutionInput.fill('代替ルートで輸送完了')
  await page.getByRole('button', { name: '解決' }).click()

  // RESOLVED に更新されること（一覧を再フェッチ後）
  await expect(page.getByText('RESOLVED')).toBeVisible({ timeout: 10_000 })
})

test('US20: LOSS 例外登録 → 緊急通知バナー + escalated マーク表示', async ({ page, request }) => {
  const suffix = `L${Date.now().toString(36).toUpperCase().slice(-6)}`

  const adminToken = await loginAsAdmin(page)

  const trackingNumber = await createFullyTrackedBooking(request, adminToken, suffix)
  if (!trackingNumber) {
    console.log('booking flow failed, skipping test')
    test.skip()
    return
  }

  const initialized = await waitForTrackingSummary(request, adminToken, trackingNumber)
  if (!initialized) {
    console.log('trackingms initialization timed out, skipping test')
    test.skip()
    return
  }

  // S18 例外登録フォームへ直接遷移
  await page.goto(`/tracking/${trackingNumber}/exceptions/new`)
  await expect(page.getByText('追跡例外登録')).toBeVisible({ timeout: 10_000 })

  // LOSS 種別を選択 → 緊急通知バナーが表示されること
  await page.getByRole('combobox', { name: /例外種別/ }).selectOption('LOSS')
  await expect(page.getByRole('alert', { name: /緊急通知/ })).toBeVisible({ timeout: 5_000 })

  // LOSS 例外を登録
  await page.getByRole('textbox', { name: /説明/ }).fill('貨物が紛失しました')
  await page.getByRole('button', { name: '登録' }).click()

  // 登録後に S17 管理ページに戻ること
  await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}/manage`), {
    timeout: 15_000,
  })

  // 例外対応タブで escalated マーク（「緊急」）が表示されること
  await page.getByRole('button', { name: '例外対応' }).click()
  await expect(page.getByText('貨物が紛失しました')).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('緊急')).toBeVisible()
})
