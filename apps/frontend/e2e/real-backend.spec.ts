import { expect, type APIRequestContext, test } from '@playwright/test'
import { businessLocalDateTime } from './support/business-time.js'

type AuthenticatedApi = {
  headers: { Authorization: string }
  shipperId: number
}

async function salesApi(request: APIRequestContext): Promise<AuthenticatedApi> {
  const login = await request.post('/api/v1/auth/login', {
    data: { userId: 'sales01', password: 'password' },
  })
  expect(login.ok()).toBeTruthy()
  const session = await login.json()
  const headers = { Authorization: `Bearer ${session.token}` }

  const shippers = await request.get('/api/v1/shippers', { headers })
  expect(shippers.ok()).toBeTruthy()
  const existing = await shippers.json()
  if (existing.length > 0) {
    return { headers, shipperId: existing[0].id }
  }

  const registered = await request.post('/api/v1/shippers', {
    headers,
    data: {
      type: 'INDIVIDUAL',
      name: '実 E2E 荷主',
      email: `real-e2e-${Date.now()}@example.com`,
      address: '東京都港区港南 1-1-1',
      phone: '03-0000-0000',
      contractNumber: null,
      discountRatePercent: null,
      registerAnyway: true,
    },
  })
  expect(registered.ok()).toBeTruthy()
  const shipper = await registered.json()
  return { headers, shipperId: shipper.id }
}

async function ensureBookingWaitingForRouting(request: APIRequestContext): Promise<string> {
  const { headers, shipperId } = await salesApi(request)
  const booked = await request.post('/api/v1/bookings', {
    headers,
    data: {
      shipperId,
      type: 'GENERAL',
      weightKg: 900,
      quantity: null,
      description: '実 E2E 経路設計用',
      lengthCm: null,
      widthCm: null,
      heightCm: null,
      originUnLocode: 'JPTYO',
      destinationUnLocode: 'USLAX',
      departureDate: null,
      arrivalDeadline: businessLocalDateTime(120, '00:00').slice(0, 10),
      hazardousClass: null,
      unNumber: null,
      properShippingName: null,
      minCelsius: null,
      maxCelsius: null,
    },
  })
  expect(booked.ok()).toBeTruthy()
  const cargo = await booked.json()

  const requested = await request.post(
    `/api/v1/bookings/${encodeURIComponent(cargo.bookingId)}/routing-request`,
    { headers, data: {} },
  )
  expect(requested.ok()).toBeTruthy()
  return cargo.bookingId
}

/**
 * モックを実物に差し替えて 1 本通す（IT1 の Try 8）。
 *
 * <strong>日付はリテラルで書かない。</strong>固定の年月日を書くと、その日を過ぎた瞬間に
 * 原因と無関係な複数のテストが同時に赤くなる。業務タイムゾーンのヘルパで作る。
 *
 * モックで検証した機能は、実物を 1 本通すまで「動く」と言わない。MSW は仕様の写しであり、
 * 写し間違いはモックが緑のままでは分からない。
 *
 * 実行には k8s 統合環境の Gateway・authms・bookingms が動いていることが要る。
 * 画面と API を同じ Ingress（http://localhost）から通す。
 */
test.beforeAll(async ({ request }) => {
  await salesApi(request)
})

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
    await page.getByLabel('到着期限').fill(businessLocalDateTime(120, '00:00').slice(0, 10))
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
    await page.getByLabel('1 区間目の出発日時').fill(businessLocalDateTime(30, '09:00'))
    await page.getByLabel('1 区間目の到着日時').fill(businessLocalDateTime(47, '12:00'))
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
    await page.getByLabel('到着期限').fill(businessLocalDateTime(120, '00:00').slice(0, 10))
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
    await page.getByLabel('到着期限').fill(businessLocalDateTime(120, '00:00').slice(0, 10))
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

    // 候補があれば、確定して予約詳細に旅程が出るところまで通す（US09 / US11）。
    // モックでは利用者を切り替えた往復ができないため、この形は実バックエンドでしか
    // 確かめられない（IT4 Try 7）
    const hasCandidates = await page
      .getByRole('button', { name: 'この経路を選ぶ' })
      .first()
      .isVisible()
      .catch(() => false)
    if (!hasCandidates) {
      test.info().annotations.push({
        type: 'skipped',
        description: '航海の登録が無く候補が 0 件のため、確定までは通していない',
      })
      return
    }

    await page.getByRole('button', { name: 'この経路を選ぶ' }).first().click()
    await expect(page.getByText('この経路で確定しますか')).toBeVisible()
    await page.getByRole('button', { name: 'この経路で確定する' }).click()

    // 確定できたことは、予約詳細に旅程が出ていることで分かる
    await expect(page).toHaveURL(new RegExp(`/booking/${bookingId}$`))
    await expect(
      page.getByRole('heading', { name: /割り当て経路（旅程・\d+ 区間）/ }),
    ).toBeVisible()

    // 状態が両方動いていることを、画面の言葉で確かめる（ADR-020 決定 2）
    await expect(page.getByText('経路が決まりました').last()).toBeVisible()

    // 経路が決まった予約も経路設計者に開いたまま（ADR-020 決定 3）。
    // 開けなくなると、差し替えの入口がどこにも無くなる
    await expect(page.getByRole('link', { name: '経路を見直す' })).toBeVisible()
  })

  /**
   * 画面が送る値の型を、サーバが受け取る型と突き合わせる（IT3 Try 4）。
   *
   * IT3 では画面が日付を送りサーバが日時で受け取っており、モックが文字列の前方比較で
   * 「たまたま動く」ため、単体も E2E も緑のまま実バックエンドでだけ落ちた。
   *
   * <strong>URL を手書きしない。</strong>手書きすると、画面が実際に組む URL
   * （`features/routing/api.ts`）を一度も通らず、「サーバが受け取れる」ことしか
   * 確かめられない。画面を操作して、実際に飛んだ問い合わせを見る。
   */
  test('画面が組む経路候補の URL は、期限を日付で送る', async ({ page, request }) => {
    const bookingId = await ensureBookingWaitingForRouting(request)

    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('routing01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: bookingId }).click()

    const [routeRequest] = await Promise.all([
      page.waitForRequest((candidate) => candidate.url().includes('/api/v1/routes?')),
      page.getByRole('link', { name: '経路を割り当て' }).click(),
    ])

    const sent = new URL(routeRequest.url()).searchParams
    // 日付のまま送る。日時に変換しない（ADR-017 決定 3）
    expect(sent.get('deadline')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(sent.get('cargoType')).not.toBeNull()

    // サーバはそれを業務タイムゾーンの当日終わりに直す
    await expect(page.getByText(/候補 \d+ 件（推奨順）|見つかりませんでした/)).toBeVisible()
  })

  /**
   * 値の形が壊れていても、権限が無ければ 403（ADR-016）。
   *
   * <p><strong>この欠陥は実バックエンドでのみ再現する。</strong>パラメータを `LocalDate` や
   * 列挙型で受け取ると、Spring は認可より先に変換を試み、失敗すると既定の 400 を返す。
   * MockMvc の変換は同じようには振る舞わないため、単体の検査では緑のまま通った。
   * 見つかった場所に回帰を置く。
   */
  test('値の形が壊れていても、権限が無ければ 403（入力仕様を教えない）', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    type Probe = { status: number }
    const probes: Probe[] = await page.evaluate(async () => {
      const token = JSON.parse(sessionStorage.getItem('cargo-tracker-auth') ?? '{}')?.state?.token
      const call = async (query: string) => {
        const response = await fetch(`/api/v1/routes?${query}`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        return { status: response.status }
      }
      return [
        await call('origin=JPTYO&destination=USLAX&deadline=not-a-date&cargoType=GENERAL'),
        await call('origin=JPTYO&destination=USLAX&deadline=2026-11-30&cargoType=BOGUS'),
        await call('origin=JPTYO&destination=USLAX&deadline=2026-11-30&cargoType=GENERAL&maxTransshipments=abc'),
      ]
    })

    expect(probes.map((probe) => probe.status)).toEqual([403, 403, 403])
  })
})
