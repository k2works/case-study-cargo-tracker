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

test('03-shipper-corporate（法人契約情報の入力）', async ({ page }) => {
  await login(page, 'sales01')
  await page.goto('/booking/shippers/new')
  await page.getByLabel('荷主種別').selectOption('CORPORATE')
  await page.getByLabel('氏名/社名').fill('丸紅商事株式会社')
  await page.getByLabel('メールアドレス').fill('corp-manual@example.com')
  await page.getByLabel('住所').fill('東京都千代田区丸の内 1-1-1')
  await page.getByLabel('契約番号').fill('CN-2026-0001')
  await page.getByLabel('割引率（%）').fill('12.5')
  await expect(page.getByLabel('契約番号')).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-corporate.png`, fullPage: true })
})

test('04-booking-register（貨物予約の登録）', async ({ page }) => {
  await login(page, 'sales01')
  await page.goto('/booking/new')
  await page.getByLabel('荷主').selectOption({ index: 1 })
  await page.getByLabel('重量（kg）').fill('12000')
  await page.getByLabel('個数').fill('20')
  await page.getByLabel('品名').fill('電子部品')
  await page.getByLabel('長さ（cm）').fill('120')
  await page.getByLabel('幅（cm）').fill('80')
  await page.getByLabel('高さ（cm）').fill('100')
  await page.getByLabel('出発地').selectOption('JPTYO')
  await page.getByLabel('目的地').selectOption('USLAX')
  await page.getByLabel('希望出発日').fill('2027-09-01')
  await page.getByLabel('到着期限').fill('2027-09-20')

  await page.screenshot({ path: `${ASSETS}/04-booking-register.png`, fullPage: true })
})

test('04-booking-list（貨物予約の一覧）', async ({ page }) => {
  await login(page, 'sales01')

  // 一覧に何も無い状態を撮ると、読者は列の意味を確かめられない
  await page.goto('/booking/new')
  await page.getByLabel('荷主').selectOption({ index: 1 })
  await page.getByLabel('貨物種別').selectOption('HAZARDOUS')
  await page.getByLabel('重量（kg）').fill('500')
  await page.getByLabel('出発地').selectOption('JPTYO')
  await page.getByLabel('目的地').selectOption('USLAX')
  await page.getByLabel('到着期限').fill('2027-09-20')
  await page.getByLabel('危険物クラス').fill('Class 3')
  await page.getByLabel('UN 番号').fill('UN1263')
  await page.getByLabel('正式品名').fill('PAINT')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByRole('status')).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/04-booking-list.png`, fullPage: true })
})
