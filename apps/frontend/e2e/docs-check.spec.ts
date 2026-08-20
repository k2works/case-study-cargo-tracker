import { expect, test } from '@playwright/test'

const DOCS = process.env.DOCS_URL

test.skip(DOCS === undefined, 'DOCS_URL が未設定のため飛ばす')

test('ポータルからユーザーマニュアルを読める', async ({ page }) => {
  await page.goto(`${DOCS}/`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('link', { name: 'ユーザーマニュアル' }).click()

  // 「まだ作成していません」に着かないこと
  await expect(page.getByRole('heading', { name: /ユーザーマニュアル/ }).first()).toBeVisible({
    timeout: 30000,
  })
  await expect(page.getByText('まだ作成していません')).toHaveCount(0)

  // 業務フローから貨物予約の章へ辿れること
  await page.goto(`${DOCS}/docs/manual/01-業務フロー/`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('link', { name: '貨物予約', exact: true }).first().click()
  await expect(page.getByRole('heading', { name: /04 貨物予約/ })).toBeVisible({ timeout: 30000 })
})

test('古い入口は正しい場所へ送る', async ({ page }) => {
  await page.goto(`${DOCS}/manual/`, { waitUntil: 'domcontentloaded' })

  await expect(page).toHaveURL(/\/docs\/manual\//, { timeout: 30000 })
})
