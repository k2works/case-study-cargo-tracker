import { test, expect } from '@playwright/test'

/**
 * US-UI-r フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン成功
 *   2. 荷主一覧 (/shippers) に遷移していること
 *   3. 「新規登録」リンクから荷主登録ページに遷移
 *   4. 個人荷主を登録（一意なメールアドレス）
 *   5. 荷主一覧に登録した名前が表示されることを確認
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), gatewayms (:8080) が起動済み
 *   - V005 で投入される admin ユーザー（パスワード: password）が DB に存在
 *
 * 詳細は e2e/README.md を参照。
 */
test('US-UI-r: ログイン → 個人荷主登録 → 一覧表示', async ({ page }) => {
  // 一意なテストデータ（重複登録を避けるため）
  const uniqueSuffix = Date.now().toString(36)
  const shipperName = `E2Eテスト荷主-${uniqueSuffix}`
  const shipperEmail = `e2e-${uniqueSuffix}@example.com`

  // 1. ログイン画面へ
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible()

  // 2. ログイン（フォームにはデフォルト値 admin/password がプリセット済み）
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()

  // 3. 荷主一覧 (/shippers) へ自動遷移することを確認
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })
  await expect(page.getByRole('heading', { name: '荷主一覧' })).toBeVisible()

  // 4. 新規登録リンクをクリック
  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page).toHaveURL(/\/shippers\/new/)
  await expect(page.getByRole('heading', { name: '荷主の登録' })).toBeVisible()

  // 5. 個人荷主を入力
  await page.locator('#shipper-name').fill(shipperName)
  await page.locator('#shipper-email').fill(shipperEmail)
  await page.locator('#shipper-phone').fill('090-1234-5678')
  // 種別はデフォルトで INDIVIDUAL（個人）

  // 6. 登録実行
  await page.getByRole('button', { name: '登録する' }).click()

  // 7. 荷主一覧に戻ることを確認
  await expect(page).toHaveURL(/\/shippers$/, { timeout: 10_000 })

  // 8. 登録した荷主名が一覧に表示されること
  await expect(page.getByText(shipperName)).toBeVisible({ timeout: 10_000 })
})
