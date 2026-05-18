import { test, expect } from '@playwright/test'

/**
 * US04 / US05 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン成功
 *   2. 荷主登録（予約に必要な shipper を新規作成）
 *   3. 予約管理メニュー → 予約一覧 (/bookings) へ遷移
 *   4. 新規登録から一般貨物の予約を登録
 *   5. 予約一覧に登録した予約が表示されることを確認
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), gatewayms (:8080) が起動済み
 *   - V005 で投入される admin ユーザー（パスワード: password、ROLE_ADMIN）が DB に存在
 *
 * 詳細は e2e/README.md を参照。
 */
test('US04: ログイン → 荷主登録 → 予約登録 → 予約一覧で表示', async ({ page }) => {
  // 一意なテストデータ
  const uniqueSuffix = Date.now().toString(36)
  const shipperName = `E2E予約-荷主-${uniqueSuffix}`
  const shipperEmail = `e2e-booking-${uniqueSuffix}@example.com`
  const productName = `E2E予約-貨物-${uniqueSuffix}`

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })

  // 2. 荷主を登録（予約に shipperId が必要）
  await page.getByRole('link', { name: '荷主管理', exact: true }).first().click()
  await expect(page).toHaveURL(/\/shippers/, { timeout: 10_000 })
  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page).toHaveURL(/\/shippers\/new/)
  await page.locator('#shipper-name').fill(shipperName)
  await page.locator('#shipper-email').fill(shipperEmail)
  await page.locator('#shipper-phone').fill('090-1234-5678')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page).toHaveURL(/\/shippers$/, { timeout: 10_000 })
  await expect(page.getByText(shipperName)).toBeVisible({ timeout: 10_000 })

  // 3. 荷主一覧から shipperId（先頭の数値 ID は表示されない → 直接 API で取得した shipperId を使う代わりに、
  //    ここでは登録後にレスポンスから shipperId を確定するのは難しいため、表示中の荷主のうち
  //    最後に登録したものが先頭 or どこかに表示されることを前提に shipperId は固定的に「1」を使うのは脆い。
  //    解決策: ナビゲーションから「予約管理」へ移動し、フォームで shipperId を直接入力する。
  //    まず荷主一覧から登録した荷主に紐づく shipperCode を取得（shipperCode は表示されているはず）。

  // 4. 予約管理メニューへ遷移（ナビバーの「予約」リンクを使用）
  await page.getByRole('link', { name: '予約', exact: true }).click()
  await expect(page).toHaveURL(/\/bookings$/, { timeout: 10_000 })
  await expect(page.getByRole('heading', { name: '予約一覧' })).toBeVisible()

  // 5. 新規予約登録画面へ
  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page).toHaveURL(/\/bookings\/new/)
  await expect(page.getByRole('heading', { name: '予約の登録' })).toBeVisible()

  // 6. 予約情報を入力（shipperId は荷主登録 API のレスポンスから取得する必要があるが、
  //    E2E では先に登録した荷主の ID が連番採番されることを前提に、API 呼び出しで最新を取得する）
  const apiBaseURL = process.env.API_BASE_URL ?? 'http://localhost:8080'
  const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  const shippersResp = await page.request.get(`${apiBaseURL}/api/v1/shippers`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(shippersResp.ok()).toBeTruthy()
  const shippers = (await shippersResp.json()) as Array<{ id: number; email: string }>
  const newShipper = shippers.find((s) => s.email === shipperEmail)
  expect(newShipper).toBeDefined()

  await page.locator('#booking-shipper-id').fill(String(newShipper!.id))
  // 貨物種別はデフォルト GENERAL のまま
  await page.locator('#booking-weight').fill('100')
  await page.locator('#booking-quantity').fill('1')
  await page.locator('#booking-product-name').fill(productName)
  await page.locator('#booking-length').fill('100')
  await page.locator('#booking-width').fill('50')
  await page.locator('#booking-height').fill('30')
  await page.locator('#booking-origin').fill('JPYOK')
  await page.locator('#booking-destination').fill('USLAX')
  await page.locator('#booking-deadline').fill('2099-12-31')

  // 7. 登録実行
  await page.getByRole('button', { name: '登録する' }).click()

  // 8. 一覧に戻る
  await expect(page).toHaveURL(/\/bookings$/, { timeout: 10_000 })

  // 9. 登録した予約の品名が一覧に表示される
  await expect(page.getByText(productName)).toBeVisible({ timeout: 10_000 })
})
