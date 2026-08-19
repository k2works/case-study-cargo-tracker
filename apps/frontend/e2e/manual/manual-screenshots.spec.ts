import { expect, test, type Page } from '@playwright/test'

/**
 * docs/manual/assets/ の画面キャプチャを生成する。
 *
 * 手作業で PNG を置くと、次回の再生成で上書きされて撮り漏れに気づけない。画面を変えたら
 * このファイルにも手を入れ、同じイテレーションの中でキャプチャと本文を更新する。
 */
const ASSETS = '../../docs/manual/assets'

async function login(page: Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

async function registerShipper(page: Page, name: string, email: string) {
  await page.goto('/booking/shippers/new')
  await page.getByLabel('氏名/社名').fill(name)
  await page.getByLabel('メールアドレス').fill(email)
  await page.getByLabel('住所').fill('東京都千代田区丸の内 1-1-1')
  await page.getByLabel('連絡先（任意）').fill('03-1234-5678')
  await page.getByRole('button', { name: '登録する' }).click()
}

test('02-login（ログイン画面）', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/02-login.png`, fullPage: true })
})

test('02-dashboard（ダッシュボード）', async ({ page }) => {
  await login(page, 'sales01')
  await expect(page.getByRole('heading', { name: '営業ダッシュボード' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/02-dashboard.png`, fullPage: true })
})

test('03-shipper-register（荷主登録）', async ({ page }) => {
  await login(page, 'sales01')
  await page.goto('/booking/shippers/new')
  await expect(page.getByRole('heading', { name: '荷主登録' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-register.png`, fullPage: true })
})

test('03-shipper-list（荷主一覧）', async ({ page }) => {
  await login(page, 'sales01')
  // 空の一覧では読者が項目の意味をつかめないため、代表的な 1 件を登録してから撮る
  await registerShipper(page, '丸の内商事株式会社', 'marunouchi@example.com')

  // 画面内の導線で移動する。goto で読み直すとモックの登録内容が消える
  await page.getByRole('link', { name: '荷主一覧へ戻る' }).click()
  await expect(page.getByRole('cell', { name: '丸の内商事株式会社' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-list.png`, fullPage: true })
})

test('03-shipper-duplicate（重複の確認）', async ({ page }) => {
  await login(page, 'sales01')
  await registerShipper(page, '大手町物流株式会社', 'otemachi@example.com')

  await page.getByRole('button', { name: '続けて登録する' }).click()
  await page.getByLabel('氏名/社名').fill('大手町物流株式会社 横浜支店')
  await page.getByLabel('メールアドレス').fill('otemachi@example.com')
  await page.getByLabel('住所').fill('神奈川県横浜市中区 2-2-2')
  await page.getByRole('button', { name: '登録する' }).click()

  await expect(page.getByText(/既に登録されています/)).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-duplicate.png`, fullPage: true })
})
