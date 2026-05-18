import { test, expect } from '@playwright/test'

/**
 * US06 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン
 *   2. 荷主登録 → 予約登録（PRELIMINARY 状態を作る）
 *   3. 予約一覧から詳細リンクを開き S10 予約詳細へ
 *   4. 「経路設計を依頼」ボタンを押下し handoff API を呼び出す
 *   5. 予約状態が ROUTING に更新される
 *   6. PRELIMINARY 以外ではボタンが消えていることを確認（同じ画面の再描画）
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), gatewayms (:8080) が起動済み
 */

test('US06: 予約引き渡しで PRELIMINARY → ROUTING に遷移する', async ({ page }) => {
  const uniqueSuffix = Date.now().toString(36)
  const shipperName = `E2E引渡-荷主-${uniqueSuffix}`
  const shipperEmail = `e2e-handoff-${uniqueSuffix}@example.com`
  const productName = `E2E引渡-貨物-${uniqueSuffix}`

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
  await page.locator('#shipper-phone').fill('090-1234-5678')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page).toHaveURL(/\/shippers$/, { timeout: 15_000 })
  await page.reload()
  await expect(page.getByText(shipperName)).toBeVisible({ timeout: 15_000 })

  // 3. shipperId を API 経由で取得
  const apiBaseURL = process.env.API_BASE_URL ?? 'http://localhost:8080'
  const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  const shippersResp = await page.request.get(`${apiBaseURL}/api/v1/shippers`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(shippersResp.ok()).toBeTruthy()
  const shippers = (await shippersResp.json()) as Array<{ id: number; email: string }>
  const newShipper = shippers.find((s) => s.email === shipperEmail)
  expect(newShipper).toBeDefined()

  // 4. 予約登録
  await page.getByRole('link', { name: '予約', exact: true }).click()
  await expect(page).toHaveURL(/\/bookings$/, { timeout: 15_000 })
  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page).toHaveURL(/\/bookings\/new/)

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
  expect(createdBody.bookingStatus).toBe('PRELIMINARY')
  const bookingId = createdBody.bookingId

  // 5. 一覧画面で Read Model 反映待ち（リロードで再取得）
  await expect(page).toHaveURL(/\/bookings$/, { timeout: 15_000 })

  // Read Model 反映を API 経由でポーリング（最大 30 秒）
  let projected = false
  for (let i = 0; i < 30; i += 1) {
    const resp = await page.request.get(`${apiBaseURL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (resp.ok()) {
      const list = (await resp.json()) as Array<{ bookingId: string; bookingStatus: string }>
      if (list.some((b) => b.bookingId === bookingId && b.bookingStatus === 'PRELIMINARY')) {
        projected = true
        break
      }
    }
    await page.waitForTimeout(1000)
  }
  expect(projected).toBeTruthy()

  // 6. 予約詳細へ
  await page.goto(`/bookings/${bookingId}`)
  await expect(page.getByTestId('booking-detail')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByTestId('booking-status')).toHaveText('PRELIMINARY')

  // 7. handoff
  const handOffResponse = page.waitForResponse(
    (resp) =>
      resp.url().includes(`/api/v1/bookings/${bookingId}/handoff`) &&
      resp.request().method() === 'POST',
  )
  await page.getByTestId('handoff-button').click()
  const handOffResult = await handOffResponse
  expect(handOffResult.ok()).toBeTruthy()

  // 8. 成功メッセージが表示される
  await expect(page.getByTestId('handoff-success')).toBeVisible({ timeout: 15_000 })

  // 9. Read Model 反映を API 経由でポーリングし ROUTING に遷移
  let routed = false
  for (let i = 0; i < 30; i += 1) {
    const resp = await page.request.get(`${apiBaseURL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (resp.ok()) {
      const list = (await resp.json()) as Array<{ bookingId: string; bookingStatus: string }>
      if (list.some((b) => b.bookingId === bookingId && b.bookingStatus === 'ROUTING')) {
        routed = true
        break
      }
    }
    await page.waitForTimeout(1000)
  }
  expect(routed).toBeTruthy()

  // 10. ページを再読み込みすると ROUTING 表示で handoff ボタンが消えている
  await page.reload()
  await expect(page.getByTestId('booking-status')).toHaveText('ROUTING')
  await expect(page.getByTestId('handoff-button')).toHaveCount(0)
})
