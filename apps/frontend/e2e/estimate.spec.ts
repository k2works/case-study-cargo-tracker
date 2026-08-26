import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

/**
 * IT12 の受け入れ。US01（輸送見積を作成する）。
 *
 * **デモ項目 5・6・7・12・13・14 に対応する**（[IT12 計画](../../../docs/development/iteration_plan-12.md)）。
 *
 * <p>US01 は**入口に戻るストーリー**である。営業担当者が最初に触る画面でありながら、
 * 料金の式（IT11）に依存するため最後に置いた。**式は billingms の 1 か所**にあり、
 * 見積と実料金が別物にならないことを統合テストで固定する（デモ 10）。
 */

/**
 * 業務タイムゾーンでの「明日」。
 *
 * <p><strong>`toISOString()` をそのまま使わない</strong>——CI（UTC）で 1 日ずれる。
 * Playwright の設定で `timezoneId: 'Asia/Tokyo'` を与えているので、ブラウザではなく
 * ここ（Node 側）で作る値を業務の暦に合わせる。
 */
function tomorrow() {
  const now = new Date()
  const business = new Date(
    now.toLocaleString('en-US', { timeZone: 'Asia/Tokyo' }),
  )
  business.setDate(business.getDate() + 1)
  return `${business.getFullYear()}-${String(business.getMonth() + 1).padStart(2, '0')}-${String(business.getDate()).padStart(2, '0')}`
}

async function logInAs(page: Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

/** 見積の作成画面を、**ダッシュボードの導線から**開く（デモ 12）。 */
async function openNewEstimate(page: Page) {
  await logInAs(page, 'sales01')
  await page.getByRole('link', { name: /見積/ }).first().click()
  await expect(page).toHaveURL(/\/booking\/estimates/)
  await page.getByRole('link', { name: '新規見積' }).click()
  await expect(page.getByRole('heading', { name: '見積の作成' })).toBeVisible()
}

/** 要件を入力する（01-1 の 5 項目）。 */
async function fillRequirements(
  page: Page,
  options: { deadline: string; cargoType?: string; destination?: string },
) {
  await page.getByLabel('出発地').selectOption('JPTYO')
  await page.getByLabel('目的地').selectOption(options.destination ?? 'USLAX')
  await page.getByLabel('希望期限').fill(options.deadline)
  await page.getByLabel('貨物種別').selectOption(options.cargoType ?? 'GENERAL')
  await page.getByLabel('重量（kg）').fill('4200')
}

test.describe('輸送見積（US01）', () => {
  /**
   * デモ 5。**4 項目を 1 つずつ突き合わせる**（IT11 Try 2）。
   *
   * 「候補が表示される」だけを見ると、**1 項目欠けても字面は満たす**。
   */
  test('デモ 5: 候補ごとに経由港・所要日数・概算料金・航海番号が出る', async ({ page }) => {
    await openNewEstimate(page)
    await fillRequirements(page, { deadline: '2027-12-31' })
    await page.getByRole('button', { name: '候補を探す' }).click()

    const candidate = page.getByTestId('route-candidate').first()
    await expect(candidate, 'ルート候補が 1 件も出ていない').toBeVisible()
    await expect(candidate, '経由港が出ていない').toContainText('経由港')
    await expect(candidate, '所要日数が出ていない').toContainText(/\d+ 日/)
    await expect(candidate, '概算料金が出ていない').toContainText('¥')
    // 種データの航海番号（`src/mocks/routes.ts` の DEMO-…）
    await expect(candidate, '航海番号が出ていない').toContainText(/DEMO-/)
  })

  /** 01-4。**見積番号が発行され、あとから開ける。** */
  test('見積を作成すると、見積番号が発行されて一覧から開ける', async ({ page }) => {
    await openNewEstimate(page)
    await fillRequirements(page, { deadline: '2027-12-31' })
    await page.getByRole('button', { name: '候補を探す' }).click()
    await page.getByRole('button', { name: '見積を作成する' }).click()

    await expect(page).toHaveURL(/\/booking\/estimates\/[0-9a-f-]{36}$/)
    const number = page.getByTestId('estimate-number')
    await expect(number, '見積番号が出ていない').toBeVisible()

    // **読み込み直さない**——モック（MSW）の状態が消え、作った見積が無かったことになる
    const created = await number.innerText()
    await page.getByRole('link', { name: '見積管理へ戻る' }).click()
    await expect(page.getByRole('heading', { name: '見積管理' })).toBeVisible()
    await expect(
      page.getByTestId('estimate-list'),
      '作成した見積が一覧に出ていない',
    ).toContainText(created)
  })

  /**
   * デモ 6。**「候補が 0 件」と「間に合う候補が 0 件」を区別する**（01-5）。
   *
   * 後者は「最短でも N 日超過します」と出す——荷主に折り返す言葉が要る。
   */
  test('デモ 6: 期限に間に合う候補が無いとき、何日超過するかが出る', async ({ page }) => {
    await openNewEstimate(page)
    // **明日までに太平洋を渡ることはできない。**種データの便は最短でも 2 週間かかる
    await fillRequirements(page, { deadline: tomorrow() })
    await page.getByRole('button', { name: '候補を探す' }).click()

    await expect(
      page.getByTestId('deadline-exceeded'),
      '間に合わないことが出ていない。営業担当者は荷主に何と言えばよいか分からない',
    ).toContainText(/最短でも .* 日超過/)
  })

  /** デモ 7。**危険物を選ぶと申告の入力が出る**（01-6）。 */
  test('デモ 7: 危険物を選ぶと、危険物申告の入力が出る', async ({ page }) => {
    await openNewEstimate(page)

    // **既存の危険物申告（US05）の項目を踏襲する**——見積と予約で項目名が違うと、
    // 営業担当者は同じものを 2 度覚えることになる
    await expect(
      page.getByLabel('UN 番号'),
      '危険物を選ぶ前から危険物申告が出ている',
    ).toHaveCount(0)

    await page.getByLabel('貨物種別').selectOption('HAZARDOUS')

    await expect(page.getByLabel('UN 番号'), '危険物申告の入力が出ていない').toBeVisible()
    await expect(page.getByLabel('危険物クラス')).toBeVisible()
    await expect(page.getByLabel('正式品名')).toBeVisible()
  })

  /**
   * デモ 14。**見積と予約の食い違いを検出する**（01-7・US04 の未達）。
   *
   * IT2 で「US01・IT12 で満たす」と記録した未達である。**Release 2.0 を閉じる前に閉じる。**
   */
  test('デモ 14: 予約登録時に、見積との食い違いが検出される', async ({ page }) => {
    await openNewEstimate(page)
    await fillRequirements(page, { deadline: '2027-12-31' })
    await page.getByRole('button', { name: '候補を探す' }).click()
    await page.getByRole('button', { name: '見積を作成する' }).click()
    await expect(page).toHaveURL(/\/booking\/estimates\/[0-9a-f-]{36}$/)

    await page.getByRole('link', { name: 'この見積で予約する' }).click()
    await expect(page.getByRole('heading', { name: '貨物予約の登録' })).toBeVisible()

    // 見積の条件は入っている（打ち直させない）。**荷主だけは見積に無い**ので選ぶ
    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    // 見積と違う重量に書き換える
    await page.getByLabel('重量（kg）').fill('99000')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(
      page.getByTestId('estimate-mismatch'),
      '見積と食い違ったまま予約が通っている。見積の意味が無くなる',
    ).toContainText(/見積.*食い違/)

    // **直したら判定し直す。**古い警告のまま押して、直した内容が無警告で通るのは危ない
    // （IT12 レビュー・user 中）。ここでは目的地を変えて、警告の中身が変わることを見る
    await page.getByLabel('目的地').selectOption('CNSHA')
    await page.getByRole('button', { name: '登録する' }).click()
    await expect(
      page.getByTestId('estimate-mismatch'),
      '条件を直したのに、前の警告のままになっている',
    ).toContainText('目的地')
  })
})

/** デモ 12・13。**ナビゲーション整合と、ロール別の到達性。** */
test.describe('見積管理の到達性', () => {
  test('デモ 12: 営業担当者は navbar とダッシュボードの両方から見積管理へ行ける', async ({
    page,
  }) => {
    await logInAs(page, 'sales01')

    await expect(
      page.getByRole('link', { name: /見積/ }).first(),
      'ダッシュボードに見積管理への導線が無い',
    ).toBeVisible()

    await page.getByRole('navigation').getByRole('link', { name: '見積管理' }).click()
    await expect(page).toHaveURL(/\/booking\/estimates/)
    await expect(page.getByRole('heading', { name: '見積管理' })).toBeVisible()
  })

  test('デモ 13: 経理担当者は見積管理を開けない', async ({ page }) => {
    await logInAs(page, 'accountant01')
    await page.goto('/booking/estimates')
    await expect(page, '経理担当者が見積管理を開けている').toHaveURL(/\/403/)
  })

  test('デモ 13: 営業担当者は入金確認を開けない', async ({ page }) => {
    await logInAs(page, 'sales01')
    await page.goto('/billing/INV-2026000001/payment')
    await expect(page, '営業担当者が入金確認を開けている').toHaveURL(/\/403/)
  })
})
