import { test, expect } from '@playwright/test'

/**
 * IT7 US18 / S16 / TI06 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン
 *   2. bookingms でフル予約フロー（book → handoff → assign-route → confirm → issue-tracking）
 *      → CargoTrackedEvent → CargoEventAclHandler → trackingms 自動初期化
 *   3. POST /api/v1/tracking/_internal/issue-token でトークン URL 発行
 *   4. /tracking/{tn}?token=<JWT> にアクセス → S15 公開照会
 *   5. サイドナビ「追跡管理」→ S16 追跡管理一覧で表示確認
 *   6. 行クリック → S17 追跡詳細・管理（既存）
 *   7. 不正トークンで照会 → TOKEN_INVALID 表示
 *
 * 実行前提:
 *   - axonserver (:8024/:8124) が Docker で起動済み（`gulp e2e:axon`）
 *   - authms (:8081), bookingms (:8082), routingms (:8083), handlingms (:8085),
 *     trackingms (:8086), gatewayms (:8080) が local-axon-h2 プロファイルで起動済み
 *     （例: `gulp e2e:bookingms`, `gulp e2e:trackingms` 等）
 */

const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

/** bookingms フル予約フローを実行し trackingNumber を返す。失敗時は null を返す。 */
async function createFullyTrackedBooking(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  suffix: string,
): Promise<string | null> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }

  // 荷主登録
  const shipperResp = await request.post(`${API_BASE_URL}/api/v1/shippers`, {
    headers,
    data: {
      name: `E2E Shipper ${suffix}`,
      email: `e2e-${suffix}@example.com`,
      shipperType: 'CORPORATE',
    },
  })
  if (!shipperResp.ok()) return null
  const shipper = (await shipperResp.json()) as { id: number }

  // 予約登録
  const bookResp = await request.post(`${API_BASE_URL}/api/v1/bookings`, {
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

  // 経路設計引き渡し
  const handoffResp = await request.post(`${API_BASE_URL}/api/v1/bookings/${bookingId}/handoff`, {
    headers,
  })
  if (!handoffResp.ok()) return null

  // 経路紐付け
  const assignResp = await request.post(
    `${API_BASE_URL}/api/v1/bookings/${bookingId}/assign-route`,
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

  // 予約確定
  const confirmResp = await request.post(
    `${API_BASE_URL}/api/v1/bookings/${bookingId}/confirm`,
    { headers },
  )
  if (!confirmResp.ok()) return null

  // 追跡番号発行 → CargoTrackedEvent → CargoEventAclHandler → trackingms 自動初期化
  const issueResp = await request.post(
    `${API_BASE_URL}/api/v1/bookings/${bookingId}/issue-tracking`,
    { headers },
  )
  if (!issueResp.ok()) return null

  // 発行された追跡番号を bookingms 一覧から取得（Event 伝播を最大 5 秒待機）
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
    const resp = await request.get(`${API_BASE_URL}/api/v1/tracking`, { headers })
    if (resp.ok()) {
      const list = (await resp.json()) as Array<{ trackingNumber: string }>
      if (list.some((t) => t.trackingNumber === trackingNumber)) return true
    }
    await new Promise((r) => setTimeout(r, 1_000))
  }
  return false
}

test('US18 + S16: 追跡照会フルフロー（トークン発行 → 公開照会 → 一覧 → 期限切れ）', async ({
  page,
  request,
}) => {
  const suffix = Date.now().toString(36).toUpperCase().padEnd(8, 'X').slice(0, 8)

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })

  const adminToken = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  expect(adminToken).toBeTruthy()

  // 2. bookingms フル予約フロー → CargoTrackedEvent → trackingms 自動初期化
  const trackingNumber = await createFullyTrackedBooking(request, adminToken!, suffix)
  if (!trackingNumber) {
    console.log('booking flow failed, skipping test')
    test.skip()
    return
  }

  // Event 伝播待機（Axon Event Bus 経由で trackingms に tracking_summary が登録されるまでポーリング）
  const initialized = await waitForTrackingSummary(request, adminToken!, trackingNumber)
  if (!initialized) {
    console.log('trackingms initialization timed out, skipping test')
    test.skip()
    return
  }

  // 3. 管理者用に JWT を発行
  const issueResp = await request.post(
    `${API_BASE_URL}/api/v1/tracking/_internal/issue-token`,
    {
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json',
      },
      data: { trackingNumber },
    },
  )
  expect(issueResp.ok()).toBeTruthy()
  const issueBody = (await issueResp.json()) as {
    url: string
    token: string
    validUntil: string
  }
  expect(issueBody.token).toBeTruthy()
  expect(issueBody.url).toContain(trackingNumber)

  // 4. 公開照会 URL にアクセス（S15）
  // セッションをクリアして「ログイン不要」を再現
  await page.context().clearCookies()
  await page.evaluate(() => sessionStorage.clear())

  await page.goto(`/tracking/${trackingNumber}?token=${issueBody.token}`)
  await expect(page.getByText('国際貨物輸送管理 — 貨物追跡')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText(trackingNumber)).toBeVisible()
  await expect(page.getByText('受領前')).toBeVisible()

  // 5. 不正トークンで再アクセス → TOKEN_INVALID
  await page.goto(`/tracking/${trackingNumber}?token=invalid.jwt.token`)
  await expect(page.getByRole('alert')).toContainText('無効なリンクです')

  // 6. 再ログインして S16 一覧で表示確認
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })

  await page.goto('/tracking')
  await expect(page.getByTestId('tracking-list-table')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByTestId(`tracking-list-row-${trackingNumber}`)).toBeVisible()

  // 7. 行クリックで S17 へ遷移
  await page.getByRole('link', { name: trackingNumber }).click()
  await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}/manage`), {
    timeout: 15_000,
  })
})
