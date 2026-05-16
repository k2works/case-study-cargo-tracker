import { test, expect } from '@playwright/test'

/**
 * US24 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン成功
 *   2. 航海スケジュール管理メニュー → 一覧 (/routing/voyages) へ遷移
 *   3. 新規登録から航海スケジュールを登録（一意な航海番号）
 *   4. 一覧に登録した航海番号が表示されることを確認
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), gatewayms (:8080) が起動済み
 *   - V005 で投入される admin ユーザー（ROLE_ADMIN）が DB に存在
 *   - routingms V001 で投入される location_master（JPYOK / USLAX / TWKHH 等）が存在
 *
 * 詳細は e2e/README.md を参照。
 */
test('US07: 航海スケジュール一覧で出発地・目的地フィルタが機能する', async ({ page }) => {
  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })

  // 2. 航海スケジュール一覧へ
  await page.getByRole('link', { name: '航海スケジュール' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages$/, { timeout: 10_000 })

  // 3. 検索フォームが表示される
  await expect(page.getByPlaceholder('出発港 (JPYOK)')).toBeVisible()
  await expect(page.getByPlaceholder('到着港 (USLAX)')).toBeVisible()
  await expect(page.getByRole('button', { name: '検索' })).toBeVisible()

  // 4. 存在しない港コードで検索すると空リストになる
  await page.getByPlaceholder('出発港 (JPYOK)').fill('ZZZZZ')
  await page.getByRole('button', { name: '検索' }).click()
  await expect(page.getByText('航海スケジュールが登録されていません。')).toBeVisible({ timeout: 5_000 })
})

test('US24: ログイン → 航海スケジュール登録 → 一覧で表示', async ({ page }) => {
  // 一意なテストデータ（航海番号 20 文字以内）
  const uniqueSuffix = Date.now().toString(36).toUpperCase().slice(-8)
  const voyageNumber = `V-E2E-${uniqueSuffix}`
  const shipName = `E2E船-${uniqueSuffix}`

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })

  // 2. 航海スケジュールメニューへ遷移
  await page.getByRole('link', { name: '航海スケジュール' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages$/, { timeout: 10_000 })
  await expect(page.getByRole('heading', { name: '航海スケジュール一覧' })).toBeVisible()

  // 3. 新規登録画面へ
  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages\/new/)
  await expect(page.getByRole('heading', { name: '航海スケジュールの登録' })).toBeVisible()

  // 4. 入力
  await page.locator('#voyage-number').fill(voyageNumber)
  await page.locator('#voyage-ship-name').fill(shipName)
  await page.locator('#voyage-carrier-code').fill('MOL')
  await page.locator('#voyage-carrier-name').fill('Mitsui O.S.K. Lines')
  await page.locator('#voyage-origin').fill('JPYOK')
  await page.locator('#voyage-destination').fill('USLAX')
  await page.locator('#voyage-departure').fill('2099-07-01T09:00')
  await page.locator('#voyage-arrival').fill('2099-07-15T18:00')

  // 寄港地 1 件目（デフォルト存在）
  await page.locator('#movement-0-from').fill('JPYOK')
  await page.locator('#movement-0-to').fill('USLAX')
  await page.locator('#movement-0-dep-time').fill('2099-07-01T09:00')
  await page.locator('#movement-0-arr-time').fill('2099-07-15T18:00')

  // 対応貨物種別はデフォルトで GENERAL がチェック済み

  // 5. 登録
  await page.getByRole('button', { name: '登録する' }).click()

  // 6. 一覧に戻る
  await expect(page).toHaveURL(/\/routing\/voyages$/, { timeout: 10_000 })

  // 7. 登録した航海番号が一覧に表示される
  await expect(page.getByText(voyageNumber)).toBeVisible({ timeout: 10_000 })
})
