import { expect, test } from '@playwright/test'

/**
 * モックを実物に差し替えて 1 本通す（IT1 の Try 8）。
 *
 * モックで検証した機能は、実物を 1 本通すまで「動く」と言わない。MSW は仕様の写しであり、
 * 写し間違いはモックが緑のままでは分からない。
 *
 * 実行には Gateway（8080）・authms・bookingms が動いていることが要る。
 * `npm run dev:api` の開発サーバー（モック無効）に対して流す。
 */
test.describe('実バックエンドでの貨物予約', () => {
  test('ログインから予約の登録・一覧までが実物で通る', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await page.goto('/booking/new')
    await page.getByLabel('荷主').selectOption({ index: 1 })
    await page.getByLabel('貨物種別').selectOption('REFRIGERATED')
    await page.getByLabel('重量（kg）').fill('800')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('SGSIN')
    await page.getByLabel('到着期限').fill('2027-12-31')
    await page.getByLabel('保管温度の下限（℃）').fill('-20')
    await page.getByLabel('保管温度の上限（℃）').fill('-15')
    await page.getByRole('button', { name: '登録する' }).click()

    // 採番は DB のシーケンスが行う（ADR-011）。形式が違えば 5 サービスの照合が壊れる
    await expect(page.getByRole('status')).toHaveText(/BKG-\d{10}/)
    await expect(page.getByRole('cell', { name: '仮受付' }).first()).toBeVisible()
    await expect(page.getByRole('cell', { name: '冷凍・冷蔵貨物' }).first()).toBeVisible()
  })
})

/**
 * IT3 の分（US24・US07・US06）を実物で 1 本通す。
 *
 * <p>モックは仕様の写しであり、写し間違いはモックが緑のままでは分からない。実際 IT3 では、
 * 引き渡しが「更新」ではなく「新しい予約の作成」になる欠陥が、実物で通したときにだけ現れた。
 */
test.describe('実バックエンドでの航海スケジュールと引き渡し', () => {
  test('経路設計者が航海を登録し、条件で絞り込める', async ({ page }) => {
    const voyageNumber = `V-REAL-${Date.now()}`

    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('routing01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await page.goto('/routing/voyages/new')
    await page.getByLabel('航海番号').fill(voyageNumber)
    await page.getByLabel('船名').fill('さくら丸')
    await page.getByLabel('運送会社').fill('日本郵船')
    await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
    await page.getByLabel('1 区間目の到着地').selectOption('USLAX')
    await page.getByLabel('1 区間目の出発日時').fill('2027-10-01T09:00')
    await page.getByLabel('1 区間目の到着日時').fill('2027-10-18T12:00')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByText(`航海 ${voyageNumber} を登録しました`)).toBeVisible()
    await page.getByRole('button', { name: '一覧で確認する' }).click()
    await expect(page.getByRole('cell', { name: voyageNumber })).toBeVisible()

    // 逆向きでは出ない。同じ港に寄ることと、その向きに運べることは別である
    await page.getByLabel('出発地').selectOption('USLAX')
    await page.getByLabel('目的地').selectOption('JPTYO')
    await page.getByRole('button', { name: '検索する' }).click()
    await expect(page.getByRole('cell', { name: voyageNumber })).toHaveCount(0)
  })

  test('営業が引き渡すと、予約が増えずに状態だけ変わる', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    // ログインの完了を待たずに次へ進むと、トークンが入る前に業務画面を開くことになる
    await expect(page).toHaveURL(/\/dashboard/)

    await page.goto('/booking/new')
    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('重量（kg）').fill('1000')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('到着期限').fill('2027-12-31')
    await page.getByRole('button', { name: '登録する' }).click()

    const status = await page.getByRole('status').textContent()
    const bookingId = /BKG-\d{10}/.exec(status ?? '')?.[0] ?? ''
    expect(bookingId).not.toBe('')
    // 行が描かれる前に数えると 0 件を基準にしてしまう
    await expect(page.getByRole('link', { name: bookingId })).toBeVisible()
    const countBefore = await page.getByRole('row').count()

    await page.getByRole('link', { name: bookingId }).click()
    await expect(page.getByText('未依頼')).toBeVisible()
    await page.getByRole('button', { name: '経路設計を依頼する' }).click()
    await expect(page.getByText('経路設計を依頼しました')).toBeVisible()

    // 更新のはずが新しい予約になっていないこと（IT3 の kind 統合環境で見つけた欠陥）
    await page.getByRole('link', { name: '一覧に戻る' }).click()
    await expect(page.getByRole('row')).toHaveCount(countBefore)
    await page.getByRole('link', { name: bookingId }).click()
    await expect(page.getByRole('cell', { name: '経路設計を依頼済み' })).toBeVisible()
  })

  /**
   * 渡した予約が、**経路設計者の一覧に出る**ところまでを 1 本で通す（IT3 の残作業 9）。
   *
   * モックの検査は画面の読み直しで状態が消えるため、利用者を切り替えられない。
   * ここは実物なので、営業が渡してから経路設計者でログインし直すところまで繋がる。
   * 2 本に分けると、「渡したこと」と「相手に見えること」が繋がっているかを誰も
   * 確かめていない状態になる。
   */
  test('営業が渡した予約が、経路設計者の一覧に出て経路候補まで辿れる', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await page.goto('/booking/new')
    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('重量（kg）').fill('1200')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('到着期限').fill('2027-12-31')
    await page.getByRole('button', { name: '登録する' }).click()

    const status = await page.getByRole('status').textContent()
    const bookingId = /BKG-\d{10}/.exec(status ?? '')?.[0] ?? ''
    expect(bookingId).not.toBe('')

    await page.getByRole('link', { name: bookingId }).click()
    await page.getByRole('button', { name: '経路設計を依頼する' }).click()
    await expect(page.getByText('経路設計を依頼しました')).toBeVisible()

    // 経路設計者に切り替える
    await page.getByRole('button', { name: 'ログアウト' }).click()
    await page.getByLabel('利用者 ID').fill('routing01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    // ログインすると、入ろうとしていた画面に戻る
    await expect(page.getByText('（経路設計者）')).toBeVisible()

    // 渡された予約が、経路設計待ちの一覧に出る
    await page.getByRole('link', { name: 'CargoTracker' }).click()
    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await expect(page.getByRole('heading', { name: '経路設計を待っている予約' })).toBeVisible()
    await page.getByRole('link', { name: bookingId }).click()

    // そこから経路設計へ進める（US08）
    await page.getByRole('link', { name: '経路を割り当て' }).click()
    await expect(page.getByRole('heading', { name: '経路設計' })).toBeVisible()
    // 候補が出るか 0 件かは航海の登録状況による。ここで確かめるのは
    // **実バックエンドが 200 で答え、画面が結果を描く**ことである
    await expect(
      page.getByText(/候補 \d+ 件（推奨順）|見つかりませんでした/),
    ).toBeVisible()
  })

  /**
   * 画面が送る値の型を、サーバが受け取る型と突き合わせる（IT3 Try 4）。
   *
   * IT3 では画面が日付を送りサーバが日時で受け取っており、モックが文字列の前方比較で
   * 「たまたま動く」ため、単体も E2E も緑のまま実バックエンドでだけ落ちた。
   * 期限は日付で送る取り決めなので（ADR-017）、実物に対して 1 度確かめる。
   */
  test('経路候補の期限は日付で送っても実バックエンドが受け取れる', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('routing01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    // **画面と同じ経路で呼ぶ。** Playwright の request はアプリのトークンを持たないため、
    // ページの中から呼ばないと「画面が送っている形」を確かめたことにならない
    type RouteProbe = { status: number; json: { appliedCriteria: { arrivalDeadline: string } } }
    const body: RouteProbe = await page.evaluate(async () => {
      const token = JSON.parse(sessionStorage.getItem('cargo-tracker-auth') ?? '{}')?.state?.token
      const response = await fetch(
        '/api/v1/routes?origin=JPTYO&destination=USLAX&deadline=2027-12-31&cargoType=GENERAL',
        { headers: { Authorization: `Bearer ${token}` } },
      )
      return { status: response.status, json: (await response.json()) as RouteProbe['json'] }
    })

    expect(body.status).toBe(200)
    // 日付が業務タイムゾーンの当日終わりに直っている（UTC の当日終わりではない）
    expect(body.json.appliedCriteria.arrivalDeadline).toMatch(/^2027-12-31T14:59:59/)
  })
})