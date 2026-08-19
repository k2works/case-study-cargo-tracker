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
    // 実際の利用者と同じ導線で入る。/login へ直接来る前提で書くと、
    // ポータルからの入口が壊れていても E2E が気づかない。
    await page.goto('/')
    await page.getByRole('link', { name: /ログイン/ }).click()
    await expect(page).toHaveURL(/\/login/)

    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()

    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page.getByRole('heading', { name: '営業ダッシュボード' })).toBeVisible()

    await page.getByRole('button', { name: 'ログアウト' }).click()
    await expect(page).toHaveURL(/\/login/)

    // ログアウト後にブラウザバックで業務画面が見えてしまうと、共用端末で
    // 「ログアウトした」という利用者の理解が裏切られる。
    await page.goBack()
    await expect(page).not.toHaveURL(/\/dashboard/)
    await expect(page.getByRole('heading', { name: '営業ダッシュボード' })).toHaveCount(0)
  })
})

test.describe('荷主の登録', () => {
  test('営業担当者は荷主を登録し、一覧で見つけられる', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()

    // 件数や画面があっても、そこへ行けなければ仕事は進まない。ダッシュボードの導線から入る
    await page.getByRole('link', { name: '荷主を登録する' }).click()
    await expect(page).toHaveURL(/\/booking\/shippers\/new/)

    const email = `e2e-${Date.now()}@example.com`
    await page.getByLabel('氏名/社名').fill('E2E 商事')
    await page.getByLabel('メールアドレス').fill(email)
    await page.getByLabel('住所').fill('東京都千代田区 1-1-1')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByText(/SHP-\d{6}/)).toBeVisible()

    await page.getByRole('link', { name: '荷主一覧へ戻る' }).click()
    await page.getByLabel('荷主を探す').fill('E2E 商事')
    await page.getByRole('button', { name: '検索' }).click()

    await expect(page.getByRole('cell', { name: 'E2E 商事' })).toBeVisible()
  })

  test('同じメールアドレスなら既存の荷主を示して選ばせる', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()

    const email = `dup-${Date.now()}@example.com`
    await page.goto('/booking/shippers/new')

    // 1 件目を登録し、そのまま「続けて登録する」で 2 件目に入る。
    // 営業担当者が続けて入力するときの実際の流れに合わせる
    await page.getByLabel('氏名/社名').fill('先に登録')
    await page.getByLabel('メールアドレス').fill(email)
    await page.getByLabel('住所').fill('大阪府大阪市 2-2-2')
    await page.getByRole('button', { name: '登録する' }).click()
    await expect(page.getByText(/SHP-\d{6}/)).toBeVisible()

    await page.getByRole('button', { name: '続けて登録する' }).click()
    await page.getByLabel('氏名/社名').fill('後から登録')
    await page.getByLabel('メールアドレス').fill(email)
    await page.getByLabel('住所').fill('大阪府大阪市 2-2-2')
    await page.getByRole('button', { name: '登録する' }).click()

    // 「登録できません」で終わらせると、営業担当者は次に何をすればよいか分からない
    await expect(page.getByText(/既に登録されています/)).toBeVisible()
    await expect(page.getByText('先に登録')).toBeVisible()
    await expect(page.getByRole('button', { name: '既存の荷主を使う' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'それでも新規で登録する' })).toBeVisible()
  })

  test('担当外のロールでは荷主画面に入れない', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('tracker01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await page.goto('/booking/shippers')

    await expect(page).toHaveURL(/\/403/)
    // 行き止まりにしない
    await expect(page.getByRole('link', { name: /ダッシュボード/ })).toBeVisible()
  })
})
