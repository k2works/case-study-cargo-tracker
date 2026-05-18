import { test, expect } from '@playwright/test'

/**
 * IT5 US15-US17 フロントエンド E2E シナリオ（荷役作業フルフロー）。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン
 *   2. handlingms に CargoSnapshot を直接 POST（IT5 暫定 ACL）
 *   3. サイドナビ「荷役作業」→ S21 履歴画面
 *   4. 「新規登録」→ S20 荷役作業記録フォーム（US15）
 *   5. 受領（RECEIVE）作業を登録 → handlingms 履歴に反映
 *   6. S21 で追跡番号検索 → 履歴に表示
 *   7. 追跡番号リンクをクリック → S17 追跡詳細・管理（US17）
 *   8. 現在の貨物情報が表示される
 *   9. 状態を IN_TRANSIT に手動更新 → 履歴に反映
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), handlingms (:8085), gatewayms (:8080) が起動済み
 *   - handlingms に CargoSnapshot ACL の REST API（IT5 暫定）が動作
 */

const HANDLING_API_BASE_URL = process.env.HANDLING_API_BASE_URL ?? 'http://localhost:8080'
const TRACKING_API_BASE_URL = process.env.TRACKING_API_BASE_URL ?? HANDLING_API_BASE_URL

test('US15-US17: 荷役作業フルフロー（受領 → 状態手動更新）', async ({ page }) => {
  const suffix = Date.now().toString(36)
  const trackingNumber = `TRK-20260720-${suffix.toUpperCase().padEnd(8, 'X').slice(0, 8)}`

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })

  const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  expect(token).toBeTruthy()

  // 2. handlingms に CargoSnapshot 直接登録（IT5 暫定 ACL）
  const snapshotResp = await page.request.post(
    `${HANDLING_API_BASE_URL}/api/v1/handling/cargo-snapshots`,
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {
        bookingId: `B-E2E-${suffix}`,
        trackingNumber,
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'DEHAM',
        cargoType: 'GENERAL',
        arrivalDeadline: '2099-12-31',
        bookingStatus: 'TRACKING_ISSUED',
      },
    },
  )
  // gateway 経由で handlingms が起動していない場合はテストをスキップ
  if (!snapshotResp.ok()) {
    test.skip()
    return
  }

  // 2.1 trackingms にも追跡集約を初期化（TI06 移管後の状態更新で必要）
  const trackingInitResp = await page.request.post(
    `${TRACKING_API_BASE_URL}/api/v1/tracking/_internal/initialize`,
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {
        trackingNumber,
        bookingId: `B-E2E-${suffix}`,
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'DEHAM',
        estimatedArrival: '2099-12-31T23:59:59',
        voyageNumber: `V-E2E-${suffix}`,
      },
    },
  )
  if (!trackingInitResp.ok()) {
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
