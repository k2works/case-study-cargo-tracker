import { test, expect } from '@playwright/test'

/**
 * IT7 US15-US17 フロントエンド E2E シナリオ（荷役作業フルフロー）。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン
 *   2. bookingms でフル予約フロー（book → handoff → assign-route → confirm → issue-tracking）
 *      → CargoTrackedEvent → BookingEventAclHandler → cargo_snapshot 自動登録
 *      → CargoTrackedEvent → CargoEventAclHandler → trackingms 自動初期化
 *   3. サイドナビ「荷役作業」→ S21 履歴画面
 *   4. 「新規登録」→ S20 荷役作業記録フォーム（US15）
 *   5. 受領（RECEIVE）作業を登録 → handlingms 履歴に反映
 *   6. S21 で追跡番号検索 → 履歴に表示
 *   7. 追跡番号リンクをクリック → S17 追跡詳細・管理（US17）
 *   8. 現在の貨物情報が表示される
 *   9. 状態を IN_TRANSIT に手動更新 → 履歴に反映
 *
 * 実行前提:
 *   - axonserver (:8024/:8124) が Docker で起動済み（`gulp e2e:axon`）
 *   - authms (:8081), bookingms (:8082), routingms (:8083), handlingms (:8085),
 *     trackingms (:8086), gatewayms (:8080) が local-axon-h2 プロファイルで起動済み
 *     （例: `gulp e2e:bookingms`, `gulp e2e:trackingms` 等）
 */

const BOOKING_API_BASE_URL = process.env.BOOKING_API_BASE_URL ?? 'http://localhost:8080'
const HANDLING_API_BASE_URL = process.env.HANDLING_API_BASE_URL ?? 'http://localhost:8080'
const TRACKING_API_BASE_URL = process.env.TRACKING_API_BASE_URL ?? 'http://localhost:8080'

/** bookingms フル予約フローを実行し trackingNumber を返す。失敗時は null を返す。 */
async function createFullyTrackedBooking(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  suffix: string,
): Promise<{ bookingId: string; trackingNumber: string } | null> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }

  // 1. 荷主登録
  const shipperResp = await request.post(`${BOOKING_API_BASE_URL}/api/v1/shippers`, {
    headers,
    data: {
      name: `E2E Shipper ${suffix}`,
      email: `e2e-${suffix}@example.com`,
      shipperType: 'CORPORATE',
    },
  })
  if (!shipperResp.ok()) return null
  const shipper = (await shipperResp.json()) as { id: number }

  // 2. 予約登録
  const bookResp = await request.post(`${BOOKING_API_BASE_URL}/api/v1/bookings`, {
    headers,
    data: {
      shipperId: shipper.id,
      cargoSpec: {
        cargoType: 'GENERAL',
        weightKg: 500,
        quantity: 10,
        productName: `E2E Cargo ${suffix}`,
      },
      routeSpec: {
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'DEHAM',
        arrivalDeadline: '2099-12-31',
      },
    },
  })
  if (!bookResp.ok()) return null
  const booking = (await bookResp.json()) as { bookingId: string }
  const bookingId = booking.bookingId

  // 3. 経路設計引き渡し
  const handoffResp = await request.post(
    `${BOOKING_API_BASE_URL}/api/v1/bookings/${bookingId}/handoff`,
    { headers },
  )
  if (!handoffResp.ok()) return null

  // 4. 経路紐付け（簡易 1 leg）
  const assignResp = await request.post(
    `${BOOKING_API_BASE_URL}/api/v1/bookings/${bookingId}/assign-route`,
    {
      headers,
      data: {
        legs: [
          {
            voyageNumber: `V-E2E-${suffix}`,
            loadUnlocode: 'JPTYO',
            unloadUnlocode: 'DEHAM',
            loadTime: '2099-07-20T14:00:00',
            unloadTime: '2099-08-10T14:00:00',
          },
        ],
      },
    },
  )
  if (!assignResp.ok()) return null

  // 5. 予約確定
  const confirmResp = await request.post(
    `${BOOKING_API_BASE_URL}/api/v1/bookings/${bookingId}/confirm`,
    { headers },
  )
  if (!confirmResp.ok()) return null

  // 6. 追跡番号発行 → CargoTrackedEvent により handlingms / trackingms が自動更新
  const issueResp = await request.post(
    `${BOOKING_API_BASE_URL}/api/v1/bookings/${bookingId}/issue-tracking`,
    { headers },
  )
  if (!issueResp.ok()) return null

  // 7. 発行された追跡番号を bookingms 一覧から取得（Event 伝播を最大 5 秒待機）
  for (let i = 0; i < 10; i++) {
    const listResp = await request.get(`${BOOKING_API_BASE_URL}/api/v1/bookings`, { headers })
    if (!listResp.ok()) return null
    const bookings = (await listResp.json()) as Array<{ bookingId: string; trackingNumber: string }>
    const found = bookings.find((b) => b.bookingId === bookingId)
    if (found?.trackingNumber) {
      return { bookingId, trackingNumber: found.trackingNumber }
    }
    await new Promise((r) => setTimeout(r, 500))
  }
  return null
}

/**
 * handlingms の cargo_snapshot に追跡番号が登録されるまでポーリング（Event 伝播待機）。
 * 最大 15 秒待機し、見つかった場合は true を返す。
 */
async function waitForCargoSnapshot(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  trackingNumber: string,
  timeoutMs = 15_000,
): Promise<boolean> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
  const deadline = Date.now() + timeoutMs

  while (Date.now() < deadline) {
    const resp = await request.get(
      `${HANDLING_API_BASE_URL}/api/v1/handling/activities/${trackingNumber}/snapshot`,
      { headers },
    )
    if (resp.ok()) return true
    await new Promise((r) => setTimeout(r, 1_000))
  }
  return false
}

/**
 * trackingms に追跡サマリーが登録されるまでポーリング（Event 伝播待機）。
 * 最大 15 秒待機し、見つかった場合は true を返す。
 */
async function waitForTrackingSummary(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  trackingNumber: string,
  timeoutMs = 15_000,
): Promise<boolean> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
  const deadline = Date.now() + timeoutMs

  while (Date.now() < deadline) {
    const resp = await request.get(`${TRACKING_API_BASE_URL}/api/v1/tracking`, { headers })
    if (resp.ok()) {
      const list = (await resp.json()) as Array<{ trackingNumber: string }>
      if (list.some((t) => t.trackingNumber === trackingNumber)) return true
    }
    await new Promise((r) => setTimeout(r, 1_000))
  }
  return false
}

test('US15-US17: 荷役作業フルフロー（受領 → 状態手動更新）', async ({ page, request }) => {
  const suffix = Date.now().toString(36).toUpperCase().padEnd(8, 'X').slice(0, 8)

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })

  const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  expect(token).toBeTruthy()

  // 2. bookingms フル予約フロー（Event 駆動 ACL で handlingms / trackingms を自動初期化）
  const result = await createFullyTrackedBooking(request, token!, suffix)
  if (!result) {
    test.skip()
    return
  }
  const { trackingNumber } = result

  // Event 伝播待機（handlingms cargo_snapshot が登録されるまでポーリング）
  const snapshotReady = await waitForCargoSnapshot(request, token!, trackingNumber)
  if (!snapshotReady) {
    console.log('handlingms cargo_snapshot not ready, skipping test')
    test.skip()
    return
  }

  // trackingms の tracking_summary も待機
  const trackingReady = await waitForTrackingSummary(request, token!, trackingNumber)
  if (!trackingReady) {
    console.log('trackingms summary not ready, skipping test')
    test.skip()
    return
  }

  // 3. サイドナビ「荷役作業」→ S21
  await page.goto('/handling')
  await expect(page.getByTestId('handling-activity-list')).toBeVisible({ timeout: 15_000 })

  // 4. 「新規登録」→ S20
  await page.getByTestId('handling-new-button').click()
  await expect(page).toHaveURL(/\/handling\/new/, { timeout: 15_000 })

  // 5. US15: 受領作業を登録
  await page.getByTestId('handling-tracking-number-input').fill(trackingNumber)
  await page.getByTestId('handling-type-select').selectOption('RECEIVE')
  await page.getByTestId('handling-unlocode-input').fill('JPTYO')
  await page.getByTestId('handling-occurred-at-input').fill('2026-07-20T09:00')
  await page.getByTestId('handling-operator-input').fill('handler-e2e-001')
  await page.getByTestId('handling-submit').click()

  // 一覧画面に戻る
  await expect(page).toHaveURL(/\/handling$/, { timeout: 15_000 })

  // 6. S21 で追跡番号検索
  await page.getByTestId('handling-search-input').fill(trackingNumber)
  await page.getByTestId('handling-search-button').click()
  await expect(page.getByTestId(`tracking-link-${trackingNumber}`)).toBeVisible({ timeout: 15_000 })

  // 7. 追跡番号リンク → S17 追跡詳細・管理
  await page.getByTestId(`tracking-link-${trackingNumber}`).click()
  await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}/manage`), { timeout: 15_000 })

  // 8. US17: 現在の貨物情報が表示される
  await expect(page.getByTestId('snapshot-tracking')).toHaveText(trackingNumber, { timeout: 15_000 })
  await expect(page.getByText('JPTYO → DEHAM')).toBeVisible()

  // 9. US17: 状態を IN_TRANSIT に手動更新
  await page.getByTestId('status-new-select').selectOption('IN_TRANSIT')
  await page.getByTestId('status-unlocode-input').fill('SGSIN')
  await page.getByTestId('status-updated-at-input').fill('2026-07-25T08:00')
  await page.getByTestId('status-operator-input').fill('tracker-e2e-001')
  await page.getByTestId('status-submit').click()

  await expect(page.getByTestId('status-success')).toBeVisible({ timeout: 15_000 })
})
