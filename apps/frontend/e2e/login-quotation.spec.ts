import { test, expect } from '@playwright/test'

/**
 * US01 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン成功
 *   2. 荷主登録（見積に必要な shipper を新規作成）
 *   3. ヘッダーの「見積作成」リンクをクリック
 *   4. 見積フォームに入力（一般貨物、出発地、目的地、希望期限、重量）
 *   5. 「見積を作成」で /quotations/:id に遷移
 *   6. 見積詳細画面で基本情報とルート候補テーブルが表示される
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), gatewayms (:8080) が起動済み
 *   - V005 で投入される admin ユーザー（ROLE_ADMIN）が DB に存在
 *
 * 詳細は e2e/README.md を参照。
 */
test('US01: ログイン → 荷主登録 → 見積作成 → 見積詳細で表示', async ({ page }) => {
  // 一意なテストデータ
  const uniqueSuffix = Date.now().toString(36)
  const shipperName = `E2E見積-荷主-${uniqueSuffix}`
  const shipperEmail = `e2e-quotation-${uniqueSuffix}@example.com`

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)

  // 2. 荷主を登録（見積に shipperId が必要）
  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page).toHaveURL(/\/shippers\/new/)
  await page.locator('#shipper-name').fill(shipperName)
  await page.locator('#shipper-email').fill(shipperEmail)
  await page.locator('#shipper-phone').fill('090-1234-5678')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page).toHaveURL(/\/shippers$/)
  // 直近登録の Read Model 反映を確実に拾うために再ロード
  await page.reload()
  await expect(page.getByText(shipperName)).toBeVisible()

  // 3. shipperId を API 経由で取得（UI 上は ID 列が表示されないため）
  const apiBaseURL = process.env.API_BASE_URL ?? 'http://localhost:8080'
  const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
  const shippersResp = await page.request.get(`${apiBaseURL}/api/v1/shippers`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(shippersResp.ok()).toBeTruthy()
  const shippers = (await shippersResp.json()) as Array<{ id: number; email: string }>
  const newShipper = shippers.find((s) => s.email === shipperEmail)
  expect(newShipper).toBeDefined()

  // 4. ヘッダーの「見積一覧」 → 一覧画面「+ 新規見積」リンクで /quotations/new に遷移
  await page.getByRole('link', { name: '見積一覧' }).click()
  await expect(page).toHaveURL(/\/quotations$/)
  await expect(page.getByRole('heading', { name: '見積一覧' })).toBeVisible()
  await page.getByRole('link', { name: '+ 新規見積' }).click()
  await expect(page).toHaveURL(/\/quotations\/new$/)
  await expect(page.getByRole('heading', { name: '輸送見積の作成' })).toBeVisible()

  // 5. 見積フォームに入力
  await page.locator('#shipperId').fill(String(newShipper!.id))
  await page.locator('#originUnLocode').fill('JPTYO')
  await page.locator('#destinationUnLocode').fill('USNYC')
  await page.locator('#arrivalDeadline').fill('2099-12-31')
  // cargoType はデフォルト GENERAL のまま
  await page.locator('#weightKg').fill('100')

  // 6. 見積を作成（POST 完了を network レベルで待ってから遷移を期待する）
  const createResponse = page.waitForResponse(
    (resp) => resp.url().includes('/api/v1/quotations') && resp.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '見積を作成' }).click()
  const response = await createResponse
  expect(response.ok()).toBeTruthy()

  // 7. /quotations/:id に遷移（Read Model 反映には数秒かかる場合がある）
  await expect(page).toHaveURL(/\/quotations\/[a-f0-9-]+$/)
  await expect(page.getByRole('heading', { name: '見積詳細' })).toBeVisible()

  // 8. 見積詳細の主要情報が表示される（Projection 反映を reload で再取得して確実化）
  await page.reload()
  await expect(page.getByTestId('quotation-detail')).toBeVisible()
  await expect(page.getByText('JPTYO').first()).toBeVisible()
  await expect(page.getByText('USNYC').first()).toBeVisible()
  // 受入条件 3: ルート候補テーブルに概算料金・所要日数が表示される
  await expect(page.getByTestId('candidates-table')).toBeVisible()
})
