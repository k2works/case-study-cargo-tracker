import { expect, test } from '@playwright/test'

/**
 * 開発環境（Heroku）に配置した画面から、実際に業務が通ることを確かめる。
 *
 * `deploy:dev:health` は各サービスの URL が 200 を返すことしか見ない。フロントが
 * 200 を返すのは「画面が表示できた」だけで、**画面から API に到達できるか**は別である。
 * 実際、Gateway の URL をビルドに渡し忘れたまま「全 URL 200」と記録したことがある。
 *
 * 対象の URL は `DEV_FRONTEND_URL` で渡す（`heroku apps:info -a {prefix}-frontend`）。
 */
const FRONTEND = process.env.DEV_FRONTEND_URL

test.skip(FRONTEND === undefined, 'DEV_FRONTEND_URL が未設定のため飛ばす')

test('開発環境の画面からログインし、荷主を登録できる', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })

  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded' })
  await page.getByLabel('利用者 ID').fill('sales01')
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()

  await expect(page).toHaveURL(/\/dashboard/, { timeout: 30000 })
  await expect(page.getByRole('heading', { name: '営業ダッシュボード' })).toBeVisible()
  // 画面から API に到達できることまで確かめる。URL が 200 を返すだけでは
  // 「画面が表示できた」しか分からない
  await page.goto(`${FRONTEND}/booking`, { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: '貨物予約' })).toBeVisible({ timeout: 30000 })
  await expect(page.getByText(/件$/).first()).toBeVisible({ timeout: 30000 })

  await page.goto(`${FRONTEND}/booking/new`, { waitUntil: 'domcontentloaded' })
  // 地点は Flyway の初期データから取る。空なら画面は API に届いていない。
  // 荷主は H2 のインメモリ DB のため dyno の再起動で消える（開発環境の仕様）
  await expect(page.getByRole('option', { name: /Tokyo（JPTYO）/ }).first()).toBeAttached({
    timeout: 30000,
  })

  // 実際に登録まで通す。画面が 200 を返すことと、業務が通ることは別
  await page.goto(`${FRONTEND}/booking/shippers/new`, { waitUntil: 'domcontentloaded' })
  await page.getByLabel('氏名/社名').fill('開発環境の確認')
  await page.getByLabel('メールアドレス').fill(`devcheck-${Date.now()}@example.com`)
  await page.getByLabel('住所').fill('東京都千代田区 1-1-1')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByText(/SHP-\d{6}/)).toBeVisible({ timeout: 30000 })

  console.log('CONSOLE ERRORS:', JSON.stringify(errors))
})
