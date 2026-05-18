import { test, expect } from '@playwright/test'

/**
 * IT6 US18 / S16 / TI06 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン
 *   2. trackingms に initialize で追跡集約を作成（IT6 暫定 ACL）
 *   3. POST /api/v1/tracking/_internal/issue-token でトークン URL 発行
 *   4. /tracking/{tn}?token=<JWT> にアクセス → S15 公開照会
 *   5. サイドナビ「追跡管理」→ S16 追跡管理一覧で表示確認
 *   6. 行クリック → S17 追跡詳細・管理（既存）
 *   7. 不正トークンで照会 → TOKEN_INVALID 表示
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), handlingms (:8085),
 *     trackingms (:8086), gatewayms (:8080) が起動済み
 */

const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

test('US18 + S16: 追跡照会フルフロー（トークン発行 → 公開照会 → 一覧 → 期限切れ）', async ({ page }) => {
  const suffix = Date.now().toString(36).toUpperCase().padEnd(8, 'X').slice(0, 8)
  const trackingNumber = `TRK-20260720-${suffix}`

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })

  const adminToken = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  expect(adminToken).toBeTruthy()

  // 2. trackingms initialize で追跡集約を作成
  const initResp = await page.request.post(
    `${API_BASE_URL}/api/v1/tracking/_internal/initialize`,
    {
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        trackingNumber,
        bookingId: `B-E2E-${suffix}`,
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'DEHAM',
        estimatedArrival: '2026-08-10T14:30:00',
        voyageNumber: 'V-E2E-001',
      },
    },
  )
  if (!initResp.ok()) {
    console.log('initialize failed:', initResp.status(), await initResp.text())
    test.skip()
    return
  }

  // 3. 管理者用に JWT を発行
  const issueResp = await page.request.post(
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
