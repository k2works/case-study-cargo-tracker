import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

/**
 * IT11 の受け入れ。US21（輸送料金の算出）・US22（法人割引）に対応する。
 *
 * **デモ項目 10 件を、この順で通す**（[IT11 計画](../../../docs/development/iteration_plan-11.md)）。
 * 実演で緑になることを「固定されている」と取り違えない——IT10 は 10 件のうち 2 件に
 * 対応する検査が無く、実装が入っていたので実演では緑になっていた。
 *
 * **経理担当者（`ROLE_ACCOUNTANT`）が初めて仕事をする IT である。** ロールは IT1 から
 * 存在するが、開いている画面が 1 つも無い状態が 10 イテレーション続いていた。
 * したがって**到達できること自体**を最初に確かめる。
 *
 * IT11 のスコープ外:
 * - 支払いの確認・入金（US23・IT12）。`PaymentStatus` は `PENDING` までしか動かさない
 * - 荷主への精算書の通知（US23・IT12）
 * - 見積との突き合わせ（US01・IT12）
 */

/** 種データ（`src/mocks/data.ts`）。引取済で、法人荷主の予約。 */
const CORPORATE_BOOKING = 'BKG-2026000004'

async function logInAs(page: Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

async function logInAsAccountant(page: Page) {
  await logInAs(page, 'accountant01')
}

test.describe('精算管理（US21・US22）', () => {
  /**
   * デモ 1。**件数から対象一覧へ辿れる**（横断規約）。
   *
   * 経理担当者は他に気づく手段を持たない——メールの仕組みは無い。
   * 件数を出すだけで導線が無いと、**気づいても仕事が進まない**。
   */
  test('デモ 1: ダッシュボードの件数から、料金未算出の予約へ辿れる', async ({ page }) => {
    await logInAsAccountant(page)

    const notice = page.getByText(/料金を算出していない引取済の予約/)
    await expect(notice, '経理担当者のダッシュボードに件数が出ていない').toBeVisible()

    await page.getByRole('link', { name: /精算管理|料金を算出/ }).first().click()
    await expect(page).toHaveURL(/\/billing/)
  })

  /**
   * デモ 2。**輸送実績が出る**（21-2）。
   *
   * **距離は持っていない**（[ADR-027](../../../docs/adr/027-transport-charge-calculation.md)
   * 決定 1）。区間数で代替し、その旨を画面に書く。
   */
  test('デモ 2: 引取済の予約を選ぶと、輸送実績が出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByRole('link', { name: new RegExp(CORPORATE_BOOKING) }).click()
    await expect(page).toHaveURL(new RegExp(`/billing/new/${CORPORATE_BOOKING}`))

    await expect(page.getByText('重量'), '重量が出ていない').toBeVisible()
    await expect(page.getByText('貨物種別'), '貨物種別が出ていない').toBeVisible()
    await expect(page.getByText('区間数'), '区間数が出ていない').toBeVisible()
    await expect(
      page.getByText(/距離は保持していません|区間数で代替/),
      '距離の代わりに区間数を使っていることを画面が言っていない',
    ).toBeVisible()
  })

  /**
   * デモ 3。**根拠が出る**（21-3・[ADR-027] 決定 1）。
   *
   * 金額そのものより「なぜその金額か」が読めることを優先する——経理担当者は
   * 請求の根拠を荷主に説明する。
   */
  test('デモ 3: 基本料金が自動で計算され、その根拠が出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    const basis = page.getByTestId('charge-basis')
    await expect(basis, '基本料金の根拠が出ていない。荷主に説明できない').toBeVisible()
    await expect(basis).toContainText('基準運賃')
    await expect(basis).toContainText('区間係数')
    await expect(basis).toContainText('重量係数')
    await expect(basis).toContainText('貨物種別係数')
  })

  /**
   * デモ 4。**法人には割引が入る**（22-1・22-2）。
   *
   * 割引率は荷主に登録済み（US03・IT2）。**経理担当者が入力するのではない**
   * ——手で入れると、契約と違う率が入る。
   */
  test('デモ 4: 法人荷主では割引率が自動で入り、割引後の金額が出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    await expect(page.getByTestId('discount-rate'), '割引率が自動で入っていない')
      .toContainText('%')
    await expect(page.getByTestId('discounted-amount'), '割引後の金額が出ていない')
      .toBeVisible()
  })

  /** デモ 5。**個人荷主には割引が適用されない**（22-3）。 */
  test('デモ 5: 個人荷主では割引が適用されない', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    const individual = page.getByTestId('unbilled-individual').first()
    await individual.click()

    await expect(
      page.getByTestId('discount-rate'),
      '個人荷主に割引率の欄が出ている。契約が無いのに割引の話が始まる',
    ).toHaveCount(0)
  })

  /**
   * デモ 6。**誤配の記録が根拠として出る**（21-6）。
   *
   * IT10 の `Misroute` が初めて読まれる。「残っている」と「読める」は別である
   * ——IT10 までは予約詳細にしか出ておらず、経理担当者はその画面を開けなかった。
   */
  test('デモ 6: 誤配した貨物では、その記録が根拠として出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByTestId('unbilled-misrouted').first().click()

    const evidence = page.getByTestId('adjustment-evidence')
    await expect(evidence, '誤配の記録が根拠として出ていない').toBeVisible()
    await expect(evidence, '外れた場所が出ていない').toContainText('Singapore')
  })

  /** デモ 7。**調整を入れると合計が変わる**（21-6）。 */
  test('デモ 7: 調整を入れると、合計が変わる', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    const before = await page.getByTestId('total-amount').textContent()

    await page.getByLabel('調整の内容').fill('遅延による減額')
    await page.getByLabel('調整額').fill('-10000')
    await page.getByRole('button', { name: '調整を追加' }).click()

    await expect(page.getByTestId('total-amount'), '調整を入れても合計が変わらない')
      .not.toHaveText(before ?? '')
  })

  /**
   * デモ 8。**確定すると金額が動かなくなる**（21-4・21-5・[ADR-027] 決定 4）。
   *
   * 請求書は荷主へ出す約束である。出したあとに黙って変わると、請求の根拠が消える。
   */
  test('デモ 8: 確定すると精算書が発行され、金額が動かなくなる', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    await page.getByRole('button', { name: '確定する' }).click()

    await expect(page, '精算書の詳細へ遷移していない').toHaveURL(/\/billing\/INV-/)
    await expect(page.getByTestId('payment-status')).toContainText('未入金')
    await expect(
      page.getByRole('button', { name: /調整を追加|確定する/ }),
      '発行後も金額を動かす操作が残っている。請求の根拠が消える',
    ).toHaveCount(0)
  })

  /** デモ 9。**割引の根拠が精算書に残る**（22-4）。 */
  test('デモ 9: 請求書詳細に、割引の根拠が記載されている', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')
    await page.getByTestId('invoice-link').first().click()

    const breakdown = page.getByTestId('amount-breakdown')
    await expect(breakdown, '基本料金が出ていない').toContainText('基本運賃')
    await expect(breakdown, '割引率が出ていない。額だけでは率を復元できない')
      .toContainText('%')
    await expect(breakdown, '割引後の金額が出ていない').toContainText('合計')
  })

  /**
   * デモ 10。**キャンセル料が算定される**（US30-9・IT9 からの持ち越し）。
   *
   * IT9 は画面に「算定していません」と書いた。本 IT でその一文が消える。
   */
  test('デモ 10: キャンセルされた予約にキャンセル料が算定される', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByTestId('unbilled-cancelled').first().click()

    const fee = page.getByTestId('cancellation-fee')
    await expect(fee, 'キャンセル料が算定されていない').toBeVisible()
    await expect(fee, '料率の根拠（キャンセル時の予約状態）が出ていない')
      .toContainText(/輸送中|輸送開始前/)
  })
})

test.describe('経理担当者の到達性（Try 5）', () => {
  /**
   * **ルートガードを通る経路で確かめる**（IT10 Try 5）。
   *
   * 画面単体のテストはルートガードを通らないため、リンクが存在することは
   * 確かめられても、**押せることは確かめられない**。IT10 は予約詳細への導線
   * 3 か所が 403 だった。
   */
  test('経理担当者は精算管理を開ける', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')
    await expect(page).toHaveURL(/\/billing/)
    await expect(page.getByRole('heading', { name: '精算管理' })).toBeVisible()
  })

  test('経理担当者以外は精算管理を開けない', async ({ page }) => {
    await logInAs(page, 'sales01')
    await page.goto('/billing')
    await expect(page, '営業担当者が精算管理を開けている').toHaveURL(/\/forbidden/)
  })
})
