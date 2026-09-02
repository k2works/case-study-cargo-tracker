import { expect, test } from '@playwright/test'

const DOCS = process.env.DOCS_URL

test.skip(DOCS === undefined, 'DOCS_URL が未設定のため飛ばす')

test('ポータルからユーザーマニュアルを読める', async ({ page }) => {
  await page.goto(`${DOCS}/`, { waitUntil: 'domcontentloaded' })
  // ポータルのカードは別タブで開くため、リンク先だけを確かめる
  const link = page.getByRole('link', { name: /ユーザーマニュアル/ })
  expect(await link.getAttribute('href')).toBe('/manual/')

  await page.goto(`${DOCS}/manual/`, { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'ユーザーマニュアル' })).toBeVisible({
    timeout: 30000,
  })
  // 以前ここに「まだ作成していません」というダミーが配信されていた
  await expect(page.getByText('まだ作成していません')).toHaveCount(0)

  // 目次から章へ、章から章へ辿れること
  await page.getByRole('link', { name: '04 貨物予約' }).click()
  await expect(page.getByRole('heading', { name: '04 貨物予約' })).toBeVisible({ timeout: 30000 })

  // キャプチャが実際に描画されること。参照が切れていても HTML は 200 を返すため、
  // 「表示されている」ではなく「読み込めた」ことを見る（loading="lazy" の完了を待つ）
  const img = page.locator('img[src*="04-booking-register.png"]')
  await img.scrollIntoViewIfNeeded()
  await expect
    // e2e の tsconfig は DOM の型を持たない（Node 側で動くため）。
    // ブラウザ内で評価する関数の引数は、その場で必要な形だけを書く
    .poll(async () => img.evaluate((e) => (e as { naturalWidth: number }).naturalWidth), {
      timeout: 30000,
    })
    .toBeGreaterThan(0)

  await page.getByRole('link', { name: /業務フロー/ }).first().click()
  await expect(page.getByRole('heading', { name: '01 業務フロー' })).toBeVisible({ timeout: 30000 })
})
