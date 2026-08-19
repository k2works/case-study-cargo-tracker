import { expect, test } from '@playwright/test'

/**
 * 本番相当ビルド（`npm run build`）に動作確認用の認証情報が入らないことを確かめる。
 *
 * <p>この検査だけが「本番のイメージに認証情報が焼き込まれた画面を出荷する」ことを止める。
 * 事前入力はビルド時に値が埋め込まれるため、実行時の設定では取り消せない。
 *
 * <p>通常の E2E（開発サーバ）では事前入力が有効なので、本 spec は本番相当ビルドを配信する
 * 専用の設定（playwright.production.config.ts）でのみ動かす。
 */
test('本番相当ビルドのログイン画面に認証情報が入っていない', async ({ page }) => {
  await page.goto('/login')

  await expect(page.getByLabel('利用者 ID')).toHaveValue('')
  await expect(page.getByLabel('パスワード')).toHaveValue('')
})

test('本番相当ビルドに開発環境の表示と利用者一覧が出ない', async ({ page }) => {
  await page.goto('/login')

  await expect(page.getByText(/開発環境/)).toHaveCount(0)
  await expect(page.getByText(/パスワードは共通/)).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'disabled01' })).toHaveCount(0)
})
