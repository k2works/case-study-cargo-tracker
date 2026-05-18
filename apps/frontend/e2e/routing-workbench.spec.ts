import { test, expect } from '@playwright/test'

/**
 * US08〜US14 フロントエンド E2E シナリオ（経路設計フルフロー）。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン
 *   2. 荷主登録 → 予約登録（PRELIMINARY）
 *   3. 予約詳細で「経路設計を依頼」→ ROUTING
 *   4. 「経路設計ワークベンチへ」リンクを開く（S14）
 *   5. 「経路候補を算出」ボタンで候補一覧を表示（US08）
 *   6. 候補 0 番を「この経路を選択」→ assign-route（US09/US11）
 *   7. 成功メッセージ後、予約詳細に戻る → ROUTE_PROPOSED
 *   8. 「予約を確定する」ボタン → CONFIRMED（US13）
 *   9. 「追跡番号を発行する」ボタン → TRACKING_ISSUED（US14）
 *  10. 追跡番号が表示される
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), gatewayms (:8080) が起動済み
 *   - routingms に JPYOK → USLAX 直行便データが存在する
 */

const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'
const ROUTING_API_BASE_URL = process.env.ROUTING_API_BASE_URL ?? 'http://localhost:8083'

async function pollBookingStatus(
  page: import('@playwright/test').Page,
  token: string,
  bookingId: string,
  expectedStatus: string,
  timeoutMs = 30_000,
): Promise<boolean> {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const resp = await page.request.get(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (resp.ok()) {
      const list = (await resp.json()) as Array<{ bookingId: string; bookingStatus: string }>
      if (list.some((b) => b.bookingId === bookingId && b.bookingStatus === expectedStatus)) {
        return true
      }
    }
    await page.waitForTimeout(1000)
  }
  return false
}

test('US08-US14: 経路設計ワークベンチフルフロー', async ({ page }) => {
  const suffix = Date.now().toString(36)
  const shipperName = `E2E経路-荷主-${suffix}`
  const shipperEmail = `e2e-routing-${suffix}@example.com`
  const productName = `E2E経路-貨物-${suffix}`

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })

  // 2. 荷主登録
  await page.getByRole('link', { name: '荷主管理', exact: true }).first().click()
  await expect(page).toHaveURL(/\/shippers/, { timeout: 15_000 })
  await page.getByRole('link', { name: '新規登録' }).click()
  await page.locator('#shipper-name').fill(shipperName)
  await page.locator('#shipper-email').fill(shipperEmail)
  await page.locator('#shipper-phone').fill('090-9999-0000')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page).toHaveURL(/\/shippers/, { timeout: 15_000 })

  const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  const shippersResp = await page.request.get(`${API_BASE_URL}/api/v1/shippers`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(shippersResp.ok()).toBeTruthy()
  const shippers = (await shippersResp.json()) as Array<{ id: number; email: string }>
  const newShipper = shippers.find((s) => s.email === shipperEmail)
  expect(newShipper).toBeDefined()

  // 3. 予約登録（経路候補が存在する出発地・目的地・期限を指定）
  await page.getByRole('link', { name: '予約', exact: true }).click()
  await page.getByRole('link', { name: '新規登録' }).click()
  await page.locator('#booking-shipper-id').fill(String(newShipper!.id))
  await page.locator('#booking-weight').fill('100')
  await page.locator('#booking-quantity').fill('1')
  await page.locator('#booking-product-name').fill(productName)
  await page.locator('#booking-length').fill('100')
  await page.locator('#booking-width').fill('50')
  await page.locator('#booking-height').fill('30')
  await page.locator('#booking-origin').fill('JPYOK')
  await page.locator('#booking-destination').fill('USLAX')
  await page.locator('#booking-deadline').fill('2099-12-31')

  const createResponse = page.waitForResponse(
    (resp) => resp.url().includes('/api/v1/bookings') && resp.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '登録する' }).click()
  const created = await createResponse
  expect(created.ok()).toBeTruthy()
  const createdBody = (await created.json()) as { bookingId: string; bookingStatus: string }
  const bookingId = createdBody.bookingId

  // Read Model に PRELIMINARY が反映されるまで待機
  expect(await pollBookingStatus(page, token!, bookingId, 'PRELIMINARY')).toBeTruthy()

  // 4. 予約詳細 → handoff → ROUTING
  await page.goto(`/bookings/${bookingId}`)
  await expect(page.getByTestId('booking-status')).toHaveText('PRELIMINARY', { timeout: 15_000 })
  await page.getByTestId('handoff-button').click()
  await expect(page.getByTestId('handoff-success')).toBeVisible({ timeout: 15_000 })
  expect(await pollBookingStatus(page, token!, bookingId, 'ROUTING')).toBeTruthy()

  await page.reload()
  await expect(page.getByTestId('booking-status')).toHaveText('ROUTING', { timeout: 15_000 })

  // 5. 経路設計ワークベンチへ（US08）
  await page.getByTestId('workbench-link').click()
  await expect(page).toHaveURL(`/routing/workbench/${bookingId}`, { timeout: 15_000 })
  await expect(page.getByTestId('booking-info')).toBeVisible()

  // routingms に経路データが存在するか確認（存在しなければスキップ）
  const candidatesResp = await page.request.get(
    `${ROUTING_API_BASE_URL}/api/v1/routing/candidates?origin=JPYOK&destination=USLAX&arrivalDeadline=2099-12-31&cargoType=GENERAL`,
  )
  if (!candidatesResp.ok()) {
    test.skip()
    return
  }
  const candidatesBody = (await candidatesResp.json()) as { candidates: unknown[] }
  if (candidatesBody.candidates.length === 0) {
    test.skip()
    return
  }

  // 6. 経路候補を算出（US08）
  await page.getByTestId('search-candidates-button').click()
  await expect(page.getByTestId('candidates-section')).toBeVisible({ timeout: 15_000 })

  // 7. 候補 0 番を選択（US09/US11）
  const assignResponse = page.waitForResponse(
    (resp) =>
      resp.url().includes(`/api/v1/bookings/${bookingId}/assign-route`) &&
      resp.request().method() === 'POST',
  )
  await page.getByTestId('select-candidate-0').click()
  const assignResult = await assignResponse
  expect(assignResult.ok()).toBeTruthy()

  await expect(page.getByTestId('assign-success')).toBeVisible({ timeout: 15_000 })

  // Read Model に ROUTE_PROPOSED が反映されるまで待機
  expect(await pollBookingStatus(page, token!, bookingId, 'ROUTE_PROPOSED')).toBeTruthy()

  // 8. 予約詳細に戻り ROUTE_PROPOSED を確認
  await page.goto(`/bookings/${bookingId}`)
  await expect(page.getByTestId('booking-status')).toHaveText('ROUTE_PROPOSED', { timeout: 15_000 })

  // 9. 予約確定（US13）
  const confirmResponse = page.waitForResponse(
    (resp) =>
      resp.url().includes(`/api/v1/bookings/${bookingId}/confirm`) &&
      resp.request().method() === 'POST',
  )
  await page.getByTestId('confirm-button').click()
  const confirmResult = await confirmResponse
  expect(confirmResult.ok()).toBeTruthy()
  expect(await pollBookingStatus(page, token!, bookingId, 'CONFIRMED')).toBeTruthy()
  await page.reload()
  await expect(page.getByTestId('booking-status')).toHaveText('CONFIRMED', { timeout: 15_000 })

  // 10. 追跡番号発行（US14）
  const issueResponse = page.waitForResponse(
    (resp) =>
      resp.url().includes(`/api/v1/bookings/${bookingId}/issue-tracking`) &&
      resp.request().method() === 'POST',
  )
  await page.getByTestId('issue-tracking-button').click()
  const issueResult = await issueResponse
  expect(issueResult.ok()).toBeTruthy()
  expect(await pollBookingStatus(page, token!, bookingId, 'TRACKING_ISSUED')).toBeTruthy()
  await page.reload()
  await expect(page.getByTestId('booking-status')).toHaveText('TRACKING_ISSUED', {
    timeout: 15_000,
  })
  await expect(page.getByTestId('tracking-number')).toBeVisible({ timeout: 15_000 })
  const trackingNumber = await page.getByTestId('tracking-number').textContent()
  expect(trackingNumber).toMatch(/^TRK-\d{8}-[A-Z0-9]{8}$/)
})
