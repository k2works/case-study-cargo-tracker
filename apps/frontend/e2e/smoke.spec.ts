import { expect, test } from '@playwright/test'

/**
 * IT1 のスモーク。US26（ログイン）・US27（ログアウト）のデモ項目に対応する。
 *
 * 実装より先に置く（Red）。画面ができるまでは落ちるが、それが「まだ通っていない」ことの
 * 唯一の可視化になる。緑になった時点で US26/US27 の受け入れが成立する。
 */
test.describe('認証のスモーク', () => {
  test('ポータルは認証なしで開ける', async ({ page }) => {
    // ロール別の到達性は認証済み利用者にしか働かない。認証の外にも入口が要る。
    const response = await page.goto('/')

    expect(response?.status()).toBe(200)
    await expect(page.getByRole('link', { name: 'ログイン' })).toBeVisible()
  })

  test('ログインしてダッシュボードに入り、ログアウトするとブラウザバックで戻れない', async ({ page }) => {
    await page.goto('/login')

    await page.getByLabel('ユーザー ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()

    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page.getByRole('heading', { name: '営業ダッシュボード' })).toBeVisible()

    await page.getByRole('button', { name: 'ログアウト' }).click()
    await expect(page).toHaveURL(/\/login/)

    // ログアウト後にブラウザバックで業務画面が見えてしまうと、共用端末で
    // 「ログアウトした」という利用者の理解が裏切られる。
    await page.goBack()
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByRole('heading', { name: '営業ダッシュボード' })).toHaveCount(0)
  })
})
