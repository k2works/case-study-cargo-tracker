import { expect, test } from '@playwright/test'

/**
 * 開発環境でだけ現れる画面のキャプチャ。
 *
 * <p>動作確認用ログインの事前入力はビルド時に決まるため、通常のキャプチャ（本番相当ビルド）
 * とは別のビルドで撮る必要がある。1 つのビルドで両方を撮ろうとすると、業務環境の説明に
 * 「開発環境です」の帯が写り込む。
 */
const ASSETS = '../../docs/manual/assets'

test('02-login-dev（開発環境のログイン画面）', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByText(/開発環境/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'disabled01' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/02-login-dev.png`, fullPage: true })
})
