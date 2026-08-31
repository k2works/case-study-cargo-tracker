import { expect, test } from '@playwright/test'

/**
 * IT14 の受け入れ。US34（業務シナリオの自動実行）・US35（工程ごとの結果の確認）。
 *
 * スコープ外（モックでは確かめられない、または実バックエンドが受け持つ）:
 * - **予約から精算までが実際に通ること**（実バックエンドの検査が受け持つ。
 *   モックで通しても、確かめたい「実際の経路が繋がっているか」は分からない）
 * - **本番環境での拒否**（起動時に落とすため、画面からは踏めない）
 * - シミュレーション由来が実データの一覧に混ざらないこと（bookingms の実 DB の検査）
 */

async function logIn(page: import('@playwright/test').Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

test.describe('業務シミュレーション（US34・US35）', () => {
  /**
   * **ダッシュボードから辿り着けること**まで確かめる。
   *
   * 画面単体の検査はルートガードを通らないため、リンクがあることは確かめられても
   * **押せることは確かめられない**。
   */
  test('システム管理者は、ダッシュボードから業務シミュレーションへ辿り着ける', async ({
    page,
  }) => {
    await logIn(page, 'admin01')

    await page.getByRole('link', { name: '業務シミュレーションを実行する' }).click()

    await expect(page).toHaveURL(/\/admin\/simulations$/)
    await expect(
      page.getByRole('heading', { name: '業務シミュレーション' }),
    ).toBeVisible()
  })

  test('実行を指示すると、その実行が一覧に現れる', async ({ page }) => {
    await logIn(page, 'admin01')
    await page.goto('/admin/simulations')

    await page.getByRole('button', { name: /実行する/ }).click()

    await expect(page.getByRole('link', { name: /^SIM-/ }).first()).toBeVisible()
  })

  test('実行を開くと、工程ごとの結果と生成した識別子が出る', async ({ page }) => {
    await logIn(page, 'admin01')
    await page.goto('/admin/simulations')

    await page.getByRole('link', { name: /^SIM-/ }).first().click()

    await expect(page.getByRole('heading', { name: /^実行 / })).toBeVisible()
    await expect(page.getByText('追跡番号発行')).toBeVisible()
    // **押した先で 403 にならない**。追跡照会は認証を求めない画面である
    await page.getByRole('link', { name: /^TRK-/ }).click()
    await expect(page).toHaveURL(/\/tracking\/TRK-/)
    await expect(page.getByText(/403|権限がありません/)).toHaveCount(0)
  })

  /** 業務の担当者には入口を出さない。出すと、押した先で 403 になる画面へ誘導する。 */
  test('営業担当者には、業務シミュレーションの入口が出ない', async ({ page }) => {
    await logIn(page, 'sales01')

    await expect(
      page.getByRole('link', { name: '業務シミュレーション' }),
    ).toHaveCount(0)
  })
})
