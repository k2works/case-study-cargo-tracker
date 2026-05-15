import { test, expect, type Page } from '@playwright/test'

const apiBaseURL = process.env.API_BASE_URL ?? 'http://localhost:8080'

/**
 * Voyage Read Model に対象航海が反映されるまで API レベルで待機する。
 *
 * UI レイヤの React Query キャッシュや表示遅延を経由しないため、
 * Axon Server の PooledStreamingEventProcessor 初回ウォームアップ
 * （Token claim + Projection）を確実に待てる。
 */
async function waitForVoyageInReadModel(
  page: Page,
  voyageNumber: string,
  predicate: (v: VoyageListItem) => boolean = () => true,
  timeoutMs = 60_000,
): Promise<VoyageListItem> {
  let lastSnapshot: VoyageListItem | undefined
  await expect
    .poll(
      async () => {
        const token = await page.evaluate(() => sessionStorage.getItem('cargo_tracker_token'))
        const resp = await page.request.get(`${apiBaseURL}/api/v1/voyages`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        if (!resp.ok()) return 'api-error'
        const list = (await resp.json()) as VoyageListItem[]
        const target = list.find((v) => v.voyageNumber === voyageNumber)
        lastSnapshot = target
        return target && predicate(target) ? 'ready' : 'not yet'
      },
      {
        message: `Read Model で voyage_number=${voyageNumber} が条件を満たすこと`,
        timeout: timeoutMs,
        intervals: [500, 1_000, 2_000, 3_000, 5_000],
      },
    )
    .toBe('ready')
  return lastSnapshot!
}

interface VoyageListItem {
  voyageNumber: string
  departureDate: string
  arrivalDate: string
  shipName: string
  status: string
}

/**
 * US25 フロントエンド E2E シナリオ。
 *
 * シナリオ:
 *   1. /login で admin/password でログイン成功
 *   2. 航海スケジュールを新規登録（編集対象を用意）
 *   3. 一覧から該当航海の「編集」リンクをクリックして S12 編集 URL に遷移
 *   4. 出発日時を変更し「更新する」を実行
 *   5. 一覧に戻り、更新後の日付が表示されることを確認
 *   6. キャンセル動作（受入条件 5）も別フローで確認
 *
 * 実行前提:
 *   - authms (:8081), bookingms (:8082), routingms (:8083), gatewayms (:8080) が起動済み
 *   - V005 で投入される admin ユーザー（ROLE_ADMIN）が DB に存在
 *
 * 詳細は e2e/README.md を参照。
 */
test('US25: ログイン → 航海登録 → 編集 → 一覧で更新内容が反映', async ({ page }) => {
  // 一意なテストデータ
  const uniqueSuffix =
    Date.now().toString(36).toUpperCase().slice(-6) +
    Math.floor(Math.random() * 10_000)
      .toString(36)
      .toUpperCase()
      .padStart(3, '0')
  const voyageNumber = `V-E-${uniqueSuffix}`
  const shipName = `E2E編集船-${uniqueSuffix}`

  // 1. ログイン
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/shippers/)

  // 2. 航海スケジュールメニューへ遷移
  await page.getByRole('link', { name: '航海スケジュール' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages$/)

  // 3. 新規航海を登録（編集対象を用意）
  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages\/new/)
  await page.locator('#voyage-number').fill(voyageNumber)
  await page.locator('#voyage-ship-name').fill(shipName)
  await page.locator('#voyage-carrier-code').fill('MOL')
  await page.locator('#voyage-carrier-name').fill('Mitsui O.S.K. Lines')
  await page.locator('#voyage-origin').fill('JPYOK')
  await page.locator('#voyage-destination').fill('USLAX')
  await page.locator('#voyage-departure').fill('2099-07-01T09:00')
  await page.locator('#voyage-arrival').fill('2099-07-15T18:00')
  await page.locator('#movement-0-from').fill('JPYOK')
  await page.locator('#movement-0-to').fill('USLAX')
  await page.locator('#movement-0-dep-time').fill('2099-07-01T09:00')
  await page.locator('#movement-0-arr-time').fill('2099-07-15T18:00')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages$/)

  // Read Model 反映を API レベルで確実に待ってから UI を再ロード。
  // 初回 Token claim を含むため最大 60s 許容。
  await waitForVoyageInReadModel(page, voyageNumber)
  await page.reload()
  await expect(page.getByTestId(`edit-link-${voyageNumber}`)).toBeVisible()

  // 4. 該当航海の「編集」リンクをクリック
  await page.getByTestId(`edit-link-${voyageNumber}`).click()
  await expect(page).toHaveURL(new RegExp(`/routing/voyages/${voyageNumber}/edit$`))
  await expect(page.getByRole('heading', { name: '航海スケジュールの更新' })).toBeVisible()
  await expect(page.getByTestId('voyage-edit-form')).toBeVisible()

  // 5. 出発日時と到着日時を変更
  await page.getByTestId('departure-date').fill('2099-07-03T09:00')
  await page.getByTestId('arrival-date').fill('2099-07-17T18:00')

  // 6. 「✎ 変更」バッジが表示される（受入条件 2: 差分プレビュー）
  await expect(page.getByTestId('change-badge').first()).toBeVisible()

  // 7. 「更新する」ボタンで送信（PUT の完了を network レベルで待つ）
  const updateResponse = page.waitForResponse(
    (resp) =>
      resp.url().includes(`/api/v1/voyages/${voyageNumber}`) && resp.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '更新する' }).click()
  const response = await updateResponse
  expect(response.ok()).toBeTruthy()

  // 8. 一覧に戻る
  await expect(page).toHaveURL(/\/routing\/voyages$/)

  // 9. Read Model 反映を API レベルで確認（VoyageScheduleUpdatedEvent → voyage テーブル）
  await waitForVoyageInReadModel(
    page,
    voyageNumber,
    (v) => v.departureDate.startsWith('2099-07-03'),
  )

  // 10. 画面でも対象行に新しい出発日が表示されることを行スコープで検証
  //     （日付テキストはテーブル全体で重複しうるので tr 単位に絞る）
  await page.reload()
  const updatedRow = page.locator('tr', { hasText: voyageNumber })
  await expect(updatedRow).toBeVisible()
  await expect(updatedRow).toContainText('2099-07-03 09:00')
})

test('US25 受入条件 5: 編集画面で「キャンセル」を押すと変更が破棄される', async ({ page }) => {
  const uniqueSuffix =
    Date.now().toString(36).toUpperCase().slice(-6) +
    Math.floor(Math.random() * 10_000)
      .toString(36)
      .toUpperCase()
      .padStart(3, '0')
  const voyageNumber = `V-C-${uniqueSuffix}`
  const shipName = `E2Eキャンセル船-${uniqueSuffix}`

  // 1. ログイン + 航海登録
  await page.goto('/login')
  await page.locator('#username').fill('admin')
  await page.locator('#password').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/shippers/)

  await page.getByRole('link', { name: '航海スケジュール' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages$/)

  await page.getByRole('link', { name: '新規登録' }).click()
  await page.locator('#voyage-number').fill(voyageNumber)
  await page.locator('#voyage-ship-name').fill(shipName)
  await page.locator('#voyage-carrier-code').fill('MOL')
  await page.locator('#voyage-carrier-name').fill('Mitsui O.S.K. Lines')
  await page.locator('#voyage-origin').fill('JPYOK')
  await page.locator('#voyage-destination').fill('USLAX')
  await page.locator('#voyage-departure').fill('2099-08-01T09:00')
  await page.locator('#voyage-arrival').fill('2099-08-15T18:00')
  await page.locator('#movement-0-from').fill('JPYOK')
  await page.locator('#movement-0-to').fill('USLAX')
  await page.locator('#movement-0-dep-time').fill('2099-08-01T09:00')
  await page.locator('#movement-0-arr-time').fill('2099-08-15T18:00')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages$/)

  // Read Model 反映を API レベルで確実に待ってから UI を再ロード
  await waitForVoyageInReadModel(page, voyageNumber)
  await page.reload()
  await expect(page.getByTestId(`edit-link-${voyageNumber}`)).toBeVisible()

  // 2. 編集画面で日付を変更（送信せず）
  await page.getByTestId(`edit-link-${voyageNumber}`).click()
  await expect(page.getByTestId('voyage-edit-form')).toBeVisible()
  await page.getByTestId('departure-date').fill('2099-08-05T09:00')

  // 3. 「キャンセル」を押すと一覧に戻り、変更内容は保存されない
  await page.getByRole('button', { name: 'キャンセル' }).click()
  await expect(page).toHaveURL(/\/routing\/voyages$/)

  // 4. 一覧には元の出発日（2099-08-01）が表示される（更新されていない）。
  //    過去 run の残データと衝突しないよう、tr 行を voyage_number で絞り込む。
  await page.reload()
  const cancelledRow = page.locator('tr', { hasText: voyageNumber })
  await expect(cancelledRow).toBeVisible()
  await expect(cancelledRow).toContainText('2099-08-01 09:00')
})
