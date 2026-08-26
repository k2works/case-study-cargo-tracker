import { expect, type APIRequestContext, test } from '@playwright/test'
import { businessLocalDateTime, utcDate } from './support/business-time.js'

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
 * 追跡番号を発行済みの貨物を用意する（US15・US16 の前提）。
 *
 * <strong>スキップせず作る</strong>（IT5 の Try 2）。「条件が揃わなければスキップ」に
 * すると、通っていないことに気づけない。予約 → 引き渡し → 経路 → 通知 → 確定 → 発行を
 * API で通す。画面から通すのは IT6 の受け入れが行っており、ここで見たいのは荷役である。
 */
async function ensureTrackedCargo(request: APIRequestContext): Promise<string> {
  return (await ensureTrackedVoyage(request)).trackingNumber
}

/** 追跡番号と、割り当てた航海番号（積込の記録に要る）。 */
async function ensureTrackedVoyage(
  request: APIRequestContext,
): Promise<{ trackingNumber: string; voyageNumber: string; bookingId: string }> {
  const bookingId = await ensureBookingWaitingForRouting(request)

  const routing = await request.post('/api/v1/auth/login', {
    data: { userId: 'routing01', password: 'password' },
  })
  expect(routing.ok()).toBeTruthy()
  const routingHeaders = { Authorization: `Bearer ${(await routing.json()).token}` }

  const routes = await request.get(
    `/api/v1/routes?origin=JPTYO&destination=USLAX`
      + `&deadline=${businessLocalDateTime(120, '00:00').slice(0, 10)}&cargoType=GENERAL`,
    { headers: routingHeaders },
  )
  expect(routes.ok()).toBeTruthy()
  const candidates = (await routes.json()).candidates as Array<{
    legs: Array<{
      voyageNumber: string
      fromUnLocode: string
      toUnLocode: string
      departureTime: string
      arrivalTime: string
    }>
  }>
  // **スキップせず作る**（IT5 の Try 2）。まっさらな DB では航海が 1 本も無い。
  // 「候補が無いので飛ばす」にすると、通っていないことに気づけない。
  if (candidates.length === 0) {
    const registered = await request.post('/api/v1/voyages', {
      headers: routingHeaders,
      data: {
        voyageNumber: `V-E2E-${Date.now()}`,
        vesselName: '実 E2E 丸',
        carrierName: '実 E2E 海運',
        supportedCargoTypes: ['GENERAL'],
        movements: [
          {
            departureUnLocode: 'JPTYO',
            arrivalUnLocode: 'USLAX',
            departureTime: `${utcDate(1)}T09:00:00Z`,
            arrivalTime: `${utcDate(20)}T09:00:00Z`,
          },
        ],
      },
    })
    expect(registered.ok(), '航海を登録できない').toBeTruthy()
    const retried = await request.get(
      `/api/v1/routes?origin=JPTYO&destination=USLAX`
        + `&deadline=${businessLocalDateTime(120, '00:00').slice(0, 10)}&cargoType=GENERAL`,
      { headers: routingHeaders },
    )
    expect(retried.ok()).toBeTruthy()
    candidates.push(...((await retried.json()).candidates as typeof candidates))
  }
  expect(
    candidates.length,
    '航海を登録しても候補が出ない。経路探索の条件を確認すること',
  ).toBeGreaterThan(0)

  const assigned = await request.put(`/api/v1/bookings/${bookingId}/route`, {
    headers: routingHeaders,
    data: {
      legs: candidates[0].legs.map((l) => ({
        voyageNumber: l.voyageNumber,
        loadUnLocode: l.fromUnLocode,
        unloadUnLocode: l.toUnLocode,
        loadTime: l.departureTime,
        unloadTime: l.arrivalTime,
      })),
      maxTransshipments: null,
    },
  })
  expect(assigned.status()).toBe(200)

  const { headers: salesHeaders } = await salesApi(request)
  const notified = await request.post(`/api/v1/bookings/${bookingId}/route-notification`, {
    headers: salesHeaders,
    data: {},
  })
  expect(notified.ok()).toBeTruthy()
  const confirmed = await request.put(`/api/v1/bookings/${bookingId}/confirm`, {
    headers: salesHeaders,
    data: {},
  })
  expect(confirmed.ok()).toBeTruthy()

  const issued = await request.post(`/api/v1/bookings/${bookingId}/tracking-number`, {
    headers: routingHeaders,
    data: {},
  })
  expect(issued.ok()).toBeTruthy()
  const trackingNumber = (await issued.json()).trackingNumber as string
  expect(trackingNumber, '追跡番号が発行されていない').toMatch(/^TRK-\d{8}-\d{4}$/)
  return { trackingNumber, voyageNumber: candidates[0].legs[0].voyageNumber, bookingId }
}

/** 荷役作業員として API を呼ぶ。 */
async function handlerHeaders(request: APIRequestContext): Promise<{ Authorization: string }> {
  const handler = await request.post('/api/v1/auth/login', {
    data: { userId: 'handler01', password: 'password' },
  })
  expect(handler.ok()).toBeTruthy()
  return { Authorization: `Bearer ${(await handler.json()).token}` }
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
    // 詳細の「経路」行を名指しで見る。ただ「未依頼」を探すと、遷移が終わるまでのあいだ
    // 一覧に残っている同じ文言（絞り込みの選択肢と他の行）に当たって strict mode 違反で落ちる
    await expect(page).toHaveURL(new RegExp(`/booking/${bookingId}$`))
    await expect(page.getByRole('row', { name: '経路 未依頼' })).toBeVisible()
    await page.getByRole('button', { name: '経路設計を依頼する' }).click()
    await expect(page.getByText('経路設計を依頼しました')).toBeVisible()

    // 更新のはずが新しい予約になっていないこと（IT3 の kind 統合環境で見つけた欠陥）
    await page.getByRole('link', { name: '一覧に戻る' }).click()
    await expect(page.getByRole('row')).toHaveCount(countBefore)
    await page.getByRole('link', { name: bookingId }).click()
    await expect(page).toHaveURL(new RegExp(`/booking/${bookingId}$`))
    await expect(page.getByRole('row', { name: '経路 経路設計を依頼済み' })).toBeVisible()
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
    // **「ボタンが無ければ成功」にしない。**ボタンを消す回帰でこのテストが緑になると、
    // US09/US11 の唯一の実バックエンド経路が判別しなくなる（IT5 レビュー 高 12）。
    // 候補の件数を先に読み、1 件以上あればボタンの存在をアサートする
    const heading = await page.getByText(/候補 \d+ 件（推奨順）|見つかりませんでした/).textContent()
    const candidateCount = Number(/候補 (\d+) 件/.exec(heading ?? '')?.[1] ?? '0')
    if (candidateCount === 0) {
      test.info().annotations.push({
        type: 'skipped',
        description: '航海の登録が無く候補が 0 件のため、確定までは通していない',
      })
      return
    }
    await expect(page.getByRole('button', { name: 'この経路を選ぶ' }).first()).toBeVisible()

    await page.getByRole('button', { name: 'この経路を選ぶ' }).first().click()
    await expect(page.getByText('この経路で確定しますか')).toBeVisible()
    await page.getByRole('button', { name: 'この経路で確定する' }).click()

    // 確定できたことは、予約詳細に旅程が出ていることで分かる
    await expect(page).toHaveURL(new RegExp(`/booking/${bookingId}$`))
    await expect(
      page.getByRole('heading', { name: /割り当て経路（旅程・\d+ 区間）/ }),
    ).toBeVisible()

    // 状態が両方動いていることを、画面の言葉で確かめる（ADR-020 決定 2）
    await expect(page.getByText('経路確定').last()).toBeVisible()

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

/**
 * 到着期限を<strong>目的地の暦</strong>で判断していることを、実物で確かめる（IT6 タスク 0.2）。
 *
 * <p>IT5 で routingms を「目的地のタイムゾーンで期限を丸める」形に直したが、確かめたのは
 * 単体テストだけだった。実環境では地点マスタ（`location.time_zone`）から引いており、
 * <strong>種データが配られていなければ既定の業務タイムゾーンに落ちて静かに元の挙動に戻る</strong>。
 * 落ちても候補が少し減るだけなので、画面を見ているだけでは気づけない。
 *
 * <p><strong>判別する形にする。</strong>期限日の 20:00Z に着く便を使う。
 *
 * <ul>
 *   <li>東京の暦では期限日の終わりは 14:59:59Z なので、この便は<strong>期限切れ</strong>になる</li>
 *   <li>ロサンゼルスの暦では翌日 06:59:59Z までなので、この便は<strong>期限内</strong>である</li>
 * </ul>
 *
 * <p>したがって候補に出れば目的地の暦、出なければ業務タイムゾーンで判断している。
 * さらに<strong>確定まで通す</strong>。確定時の再検証は bookingms が行うため、
 * 2 つの BC が同じ答えを出していないと 409 になる（IT5 で食い違っていたのはここである）。
 */
test.describe('到着期限は目的地の暦で判断する', () => {
  test('期限日の 20:00Z に着く便は、目的地が Los Angeles なら期限内として使える', async ({
    request,
  }) => {
    const deadline = utcDate(100)
    const voyageNumber = `V-TZ-${Date.now()}`

    const routing = await request.post('/api/v1/auth/login', {
      data: { userId: 'routing01', password: 'password' },
    })
    expect(routing.ok()).toBeTruthy()
    const routingHeaders = { Authorization: `Bearer ${(await routing.json()).token}` }

    const registered = await request.post('/api/v1/voyages', {
      headers: routingHeaders,
      data: {
        voyageNumber,
        vesselName: '境界丸',
        carrierName: '境界海運',
        supportedCargoTypes: ['GENERAL'],
        movements: [
          {
            departureUnLocode: 'JPTYO',
            arrivalUnLocode: 'USLAX',
            departureTime: `${utcDate(80)}T09:00:00Z`,
            // 東京の暦では期限切れ、ロサンゼルスの暦では期限内になる時刻
            arrivalTime: `${deadline}T20:00:00Z`,
          },
        ],
      },
    })
    expect(registered.ok()).toBeTruthy()

    // 予約は営業が作る。期限は同じ日付を送る（日付のまま渡す。ADR-017 決定 3）
    const { headers: salesHeaders, shipperId } = await salesApi(request)
    const booked = await request.post('/api/v1/bookings', {
      headers: salesHeaders,
      data: {
        shipperId,
        type: 'GENERAL',
        weightKg: 700,
        quantity: null,
        description: '期限の暦を確かめる',
        lengthCm: null,
        widthCm: null,
        heightCm: null,
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USLAX',
        departureDate: null,
        arrivalDeadline: deadline,
        hazardousClass: null,
        unNumber: null,
        properShippingName: null,
        minCelsius: null,
        maxCelsius: null,
      },
    })
    expect(booked.ok()).toBeTruthy()
    const bookingId = (await booked.json()).bookingId
    const handedOver = await request.post(
      `/api/v1/bookings/${encodeURIComponent(bookingId)}/routing-request`,
      { headers: salesHeaders, data: {} },
    )
    expect(handedOver.ok()).toBeTruthy()

    // 候補に出るか（routingms の判断）
    const routes = await request.get(
      `/api/v1/routes?origin=JPTYO&destination=USLAX&deadline=${deadline}&cargoType=GENERAL`,
      { headers: routingHeaders },
    )
    expect(routes.ok()).toBeTruthy()
    const candidates = (await routes.json()).candidates as Array<{
      legs: Array<{
        voyageNumber: string
        fromUnLocode: string
        toUnLocode: string
        departureTime: string
        arrivalTime: string
      }>
    }>
    const boundary = candidates.find((c) => c.legs.some((l) => l.voyageNumber === voyageNumber))
    expect(
      boundary,
      '期限日の 20:00Z 着が候補から消えている。目的地ではなく業務タイムゾーンで期限を丸めている',
    ).toBeDefined()

    // <strong>期限の検査が生きていることを対で示す。</strong>期限を 1 日前にすると、
    // ロサンゼルスの暦でも締切（当日 06:59:59Z）を過ぎるのでこの便は消える。
    // これが消えないなら「期限で絞っていない」だけであり、上の緑は何も語っていない
    const tooEarly = await request.get(
      `/api/v1/routes?origin=JPTYO&destination=USLAX&deadline=${utcDate(99)}`
        + '&cargoType=GENERAL',
      { headers: routingHeaders },
    )
    expect(tooEarly.ok()).toBeTruthy()
    const earlierCandidates = (await tooEarly.json()).candidates as Array<{
      legs: Array<{ voyageNumber: string }>
    }>
    expect(
      earlierCandidates.some((c) => c.legs.some((l) => l.voyageNumber === voyageNumber)),
      '期限を 1 日前にしても同じ便が残っている。期限で絞れていない',
    ).toBe(false)

    // 確定まで通るか（bookingms の判断。2 つの BC が同じ答えを出しているか）
    const assigned = await request.put(
      `/api/v1/bookings/${encodeURIComponent(bookingId)}/route`,
      {
        headers: routingHeaders,
        data: {
          legs: boundary?.legs.map((l) => ({
            voyageNumber: l.voyageNumber,
            loadUnLocode: l.fromUnLocode,
            unloadUnLocode: l.toUnLocode,
            loadTime: l.departureTime,
            unloadTime: l.arrivalTime,
          })),
          maxTransshipments: null,
        },
      },
    )
    expect(
      assigned.status(),
      `確定が ${assigned.status()} で断られた。候補には出るのに確定できないなら、`
        + '2 つの BC が期限を別の暦で判断している',
    ).toBe(200)
  })
})

/**
 * 提示 → 確定 → 追跡番号の発行までを、実物で 1 本通す（IT6 成功基準 1）。
 *
 * <p>ここは<strong>利用者を切り替えられる</strong>ので、営業と経路設計者の受け渡しまで
 * 繋がる。モックの検査は画面の読み直しで状態が消えるため、この形は実物でしか確かめられない。
 *
 * <p><strong>イベントが実際に届いたか</strong>（trackingms に追跡の記録が残るか）は、
 * 照会画面が US18 まで無いため画面からは見えない。運用手順書の形（`kubectl exec deploy/postgres
 * -- psql`）で確かめる（`ops` のタスクとして 5.1 に記す）。
 */
test.describe('提示から追跡番号の発行まで（実バックエンド）', () => {
  test('営業が通知して確定し、経路設計者が追跡番号を発行できる', async ({ page, request }) => {
    // 前提: 経路が決まった予約。**スキップせず作る**（IT5 の Try 2）
    const bookingId = await ensureBookingWaitingForRouting(request)

    const routing = await request.post('/api/v1/auth/login', {
      data: { userId: 'routing01', password: 'password' },
    })
    expect(routing.ok()).toBeTruthy()
    const routingHeaders = { Authorization: `Bearer ${(await routing.json()).token}` }

    const routes = await request.get(
      `/api/v1/routes?origin=JPTYO&destination=USLAX`
        + `&deadline=${businessLocalDateTime(120, '00:00').slice(0, 10)}&cargoType=GENERAL`,
      { headers: routingHeaders },
    )
    expect(routes.ok()).toBeTruthy()
    const candidates = (await routes.json()).candidates as Array<{
      legs: Array<{
        voyageNumber: string
        fromUnLocode: string
        toUnLocode: string
        departureTime: string
        arrivalTime: string
      }>
    }>
    expect(
      candidates.length,
      '候補が 1 件も無い。航海スケジュールの種データを確認すること',
    ).toBeGreaterThan(0)

    const assigned = await request.put(`/api/v1/bookings/${bookingId}/route`, {
      headers: routingHeaders,
      data: {
        legs: candidates[0].legs.map((l) => ({
          voyageNumber: l.voyageNumber,
          loadUnLocode: l.fromUnLocode,
          unloadUnLocode: l.toUnLocode,
          loadTime: l.departureTime,
          unloadTime: l.arrivalTime,
        })),
        maxTransshipments: null,
      },
    })
    expect(assigned.status()).toBe(200)

    // 営業が通知して確定する（US12・US13）
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await page.goto(`/booking/${bookingId}`)
    await expect(page.getByText(/営業担当者の手番です。経路が決まりました/)).toBeVisible()
    // メールが送られないことを画面が言う（US12-3 の代替）
    await expect(page.getByText(/この操作ではメールは送られません/)).toBeVisible()

    await page.getByRole('button', { name: '荷主へ通知する' }).click()
    await expect(page.getByText(/荷主へ通知しました/)).toBeVisible()

    await page.getByRole('button', { name: '予約を確定する' }).click()
    await expect(page.getByText(/経路設計者の手番です/)).toBeVisible()

    // 経路設計者が追跡番号を発行する（US14）
    await page.getByRole('button', { name: 'ログアウト' }).click()
    await page.getByLabel('利用者 ID').fill('routing01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page.getByText('（経路設計者）')).toBeVisible()

    await page.getByRole('link', { name: 'CargoTracker' }).click()
    await page.getByRole('link', { name: '追跡番号の発行を待っている予約を見る' }).click()
    await expect(page.getByRole('heading', { name: '追跡番号の発行を待っている予約' }))
      .toBeVisible()
    await page.getByRole('link', { name: bookingId }).click()

    await page.getByRole('button', { name: '追跡番号を発行する' }).click()

    // 形式そのものが契約になる（ADR-011 と同じ形）
    await expect(page.getByText(/^TRK-\d{8}-\d{4}$/)).toBeVisible()
    await expect(page.getByText(/荷主には自動で送られていません/)).toBeVisible()
  })

  /** US32。管理者が実在しないと、ロックされた利用者を誰も助けられない。 */
  test('管理者がロックされたアカウントを解除でき、その場でログインできる', async ({
    page,
    request,
  }) => {
    // 前提を作る: shipper01 を 5 回失敗させてロックする
    for (let attempt = 0; attempt < 5; attempt++) {
      await request.post('/api/v1/auth/login', {
        data: { userId: 'shipper01', password: 'wrong-password' },
      })
    }
    const locked = await request.post('/api/v1/auth/login', {
      data: { userId: 'shipper01', password: 'password' },
    })
    expect(locked.status(), 'ロックされていない。5 回失敗の規則が働いていない').toBe(401)

    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('admin01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await page.getByRole('link', { name: 'ロックされたアカウントを解除する' }).click()
    await expect(page.getByRole('heading', { name: 'アカウント管理' })).toBeVisible()
    await expect(page.getByText('shipper01')).toBeVisible()

    await page.getByRole('button', { name: '解除する' }).first().click()
    await expect(page.getByText(/いまロックされているアカウントはありません/)).toBeVisible()

    // 「解除した」と言えるのは、対象がログインできたときだけである
    const afterUnlock = await request.post('/api/v1/auth/login', {
      data: { userId: 'shipper01', password: 'password' },
    })
    expect(afterUnlock.ok(), '解除したのにログインできない').toBeTruthy()
  })
})

/**
 * IT7 の受け入れ（US15・US16）。**実バックエンドで荷役を記録する**。
 *
 * ここでしか確かめられないのは、**サービスをまたぐ往復**である。
 * handlingms → bookingms（ACL・追跡番号で貨物を引く）は、モックでも単体テストでも
 * 「動いているつもり」のまま壊れる。IT5 では名乗りを忘れ、実環境の往復を通すまで
 * 誰も気づかなかった。
 *
 * <p><strong>追跡の状態はここでは見ない。</strong>荷主・追跡管理者が状態を見る画面は
 * US18（IT8）であり、IT7 に外から見る入口が無い。イベントの往復は Testcontainers の
 * 往復テストが、kind での一連の遷移はクローズのデモが確かめる。
 * **見えないものを見たことにしない。**
 */
test.describe('荷役の記録（実バックエンド）', () => {
  test('荷役作業員が追跡番号から受領を記録できる', async ({ page, request }) => {
    // 前提: 追跡番号を発行済みの予約。**スキップせず作る**（IT5 の Try 2）
    const trackingNumber = await ensureTrackedCargo(request)

    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('handler01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    // 荷役作業員が、自分の仕事へダッシュボードから行ける（ロール別の到達性）
    await page.getByRole('link', { name: '荷役作業を記録する' }).click()
    await expect(page.getByRole('heading', { name: '荷役作業の記録' })).toBeVisible()

    await page.getByLabel('追跡番号').fill(trackingNumber)
    await page.getByLabel('作業の種別').selectOption('RECEIVE')
    await page.getByLabel('作業場所').selectOption('JPTYO')
    await page.getByLabel('作業日時').fill(businessLocalDateTime(0, '09:00'))
    await page.getByRole('button', { name: '記録する' }).click()

    await expect(page.getByText('記録しました。')).toBeVisible()
    // 履歴に出ることで、ACL が実際に貨物を引けたことまで分かる（予約番号が要る）
    await expect(page.getByRole('table').getByText('受領')).toBeVisible()
  })

  /** US15-6。番号を読み違えるのが最も多い。 */
  test('存在しない追跡番号は、実バックエンドでも理由を返す', async ({ request }) => {
    const headers = await handlerHeaders(request)

    const notFound = await request.post('/api/v1/handling', {
      headers,
      data: {
        trackingNumber: 'TRK-99999999-9999',
        type: 'RECEIVE',
        locationUnLocode: 'JPTYO',
        completionTime: '2026-08-23T00:00:00Z',
      },
    })

    expect(notFound.status()).toBe(404)
  })

  /**
   * US16-2。**通関を済ませてから確かめる**（IT9 で歯止めが 2 段になった）。
   *
   * <p>IT7 はこれが唯一の歯止めだったので、申告の無い貨物にそのまま引取を投げていた。
   * IT9 で通関ガードが**先に**立つため、そのままでは 409（通関申告がありません）で
   * 止まり、**荷受人の確認の守りを一度も踏まない**。踏まない検査は、その守りを外しても
   * 緑のままになる。
   */
  test('通関を済ませても、荷受人の確認がない引取は断られる', async ({ request }) => {
    const trackingNumber = await ensureTrackedCargo(request)
    const headers = await handlerHeaders(request)

    const declared = await request.post('/api/v1/customs', {
      headers,
      data: {
        trackingNumber,
        declarationNumber: `DEC-CLR-${Date.now()}`,
        declaredAt: `${utcDate(0)}T00:00:00Z`,
        remarks: '荷受人の確認を確かめる',
      },
    })
    expect(declared.status(), `申告を登録できない: ${await declared.text()}`).toBe(201)

    const tracker = await request.post('/api/v1/auth/login', {
      data: { userId: 'tracker01', password: 'password' },
    })
    expect(tracker.ok()).toBeTruthy()
    const cleared = await request.put(
      `/api/v1/customs/${(await declared.json()).declarationId}/status`,
      {
        headers: { Authorization: `Bearer ${(await tracker.json()).token}` },
        data: { status: 'CLEARED', reason: '審査完了' },
      },
    )
    expect(cleared.status(), `通関済にできない: ${await cleared.text()}`).toBe(200)

    const refused = await request.post('/api/v1/handling', {
      headers,
      data: {
        trackingNumber,
        type: 'CLAIM',
        locationUnLocode: 'USLAX',
        completionTime: `${utcDate(0)}T03:00:00Z`,
      },
    })

    expect(refused.status(), '荷受人の確認なしで引取が通った').toBe(400)
    expect((await refused.json()).message).toContain('荷受人の確認')
  })
})

/**
 * IT9 Phase 6。**実環境で 1 本通す**（成功基準 1・2・3・4）。
 *
 * <p>ここでしか出ない食い違いがある。交換機の引数は<strong>すでに交換機がある環境では
 * 宣言し直せない</strong>——Testcontainers は毎回まっさらなので通ってしまう。イベントが
 * 実際に配られることも、購読側の宣言が揃って初めて分かる。
 *
 * <p><strong>先にイメージを作り直すこと。</strong>同じタグのまま古いイメージが動いていると、
 * 直したはずの経路が緑になる（IT6 で 2 度踏んだ）。
 */
test.describe('IT9 実環境（通関とキャンセル承認）', () => {
  /** イベントは非同期に配られる。届くまで待つ——待たずに見ると、届いていても赤くなる。 */
  async function eventually(check: () => Promise<void>): Promise<void> {
    await expect.poll(async () => {
      try {
        await check()
        return 'ok'
      } catch {
        return 'not yet'
      }
    }, { timeout: 20_000, intervals: [500, 1000, 2000] }).toBe('ok')
  }

  async function trackerHeaders(request: APIRequestContext): Promise<{ Authorization: string }> {
    const tracker = await request.post('/api/v1/auth/login', {
      data: { userId: 'tracker01', password: 'password' },
    })
    expect(tracker.ok()).toBeTruthy()
    return { Authorization: `Bearer ${(await tracker.json()).token}` }
  }

  /**
   * 成功基準 1・2。申告 → 引取の拒否 → 留置 → 公開追跡に税関保留。
   *
   * <p>1 本に繋げるのは、**繋がっていることがここでしか確かめられない**からである。
   * ガードだけ・イベントだけなら、それぞれのサービスのテストが見ている。
   */
  test('通関申告から留置までを実バックエンドで通す', async ({ request }) => {
    const trackingNumber = await ensureTrackedCargo(request)
    const handler = await handlerHeaders(request)
    const tracker = await trackerHeaders(request)

    const declared = await request.post('/api/v1/customs', {
      headers: handler,
      data: {
        trackingNumber,
        declarationNumber: `DEC-${Date.now()}`,
        declaredAt: `${utcDate(0)}T00:00:00Z`,
        remarks: '実 E2E の申告',
      },
    })
    expect(declared.status(), `申告を登録できない: ${await declared.text()}`).toBe(201)
    const declarationId = (await declared.json()).declarationId

    // 成功基準 1: 通関済でない貨物の引取は断られ、いまの状態が示される
    const refused = await request.post('/api/v1/handling', {
      headers: handler,
      data: {
        trackingNumber,
        type: 'CLAIM',
        locationUnLocode: 'USLAX',
        completionTime: `${utcDate(0)}T01:00:00Z`,
      },
    })
    // 409。**入力の誤りではなく状態の衝突**である（荷受人未確認の 400 とは別の理由）
    expect(refused.status(), `通関前の引取が通った: ${await refused.text()}`).toBe(409)
    expect(await refused.text()).toContain('通関')

    // 成功基準 2: 留置にすると、追跡側に税関保留が自動で起票される
    const held = await request.put(`/api/v1/customs/${declarationId}/status`, {
      headers: tracker,
      data: { status: 'HELD', reason: '書類不備のため留置' },
    })
    expect(held.status(), '留置にできない').toBe(200)

    // 交換機・ルーティングキー・購読の宣言が全部揃って初めてここが例外になる
    await eventually(async () => {
      const publicView = await request.get(
        `/api/v1/public/tracking/${encodeURIComponent(trackingNumber)}`,
      )
      expect(publicView.ok()).toBeTruthy()
      const view = await publicView.json()
      expect(view.hasException, '留置が追跡に届いていない').toBe(true)
      // **公開画面に理由は出さない**（[ADR-025] 決定 3 と同じ立場）。
      // 荷主に見えるのは「問題が発生した」までであり、留置の事情は社内で扱う
      expect(JSON.stringify(view), '公開画面に社内の事情が漏れている').not.toContain('税関')
      expect(JSON.stringify(view)).not.toContain('書類不備')
    })

    // 種別「税関保留」が分かるのは認証の内側。ここに載らなければ追跡管理者は動けない
    // `/exceptions/open` は件数だけを返す。**件数から対象へ辿れること**を一覧で見る
    const open = await request.get('/api/v1/tracking/manage/exceptions', {
      headers: tracker,
    })
    expect(open.ok()).toBeTruthy()
    const openText = await open.text()
    expect(openText).toContain(trackingNumber)
    expect(openText, '税関保留として起票されていない').toContain('CUSTOMS_HOLD')
    expect(openText, '留置の理由が担当者に伝わっていない').toContain('書類不備のため留置')
  })

  /**
   * US16-1〜US16-3 の成功パス。**通関を済ませ、荷受人の確認を入れたら引取が通る。**
   *
   * <p>モックの E2E では書けない——通関済にできるのは追跡管理者だけで、ロールを
   * 切り替えるとページが読み直されてモックの状態が消える。**守りを 2 段とも越える道が
   * 実際に通ることは、ここでしか確かめられない。**
   */
  test('通関済なら、荷受人の確認を入れた引取が記録される', async ({ request }) => {
    const trackingNumber = await ensureTrackedCargo(request)
    const handler = await handlerHeaders(request)
    const tracker = await trackerHeaders(request)

    const declared = await request.post('/api/v1/customs', {
      headers: handler,
      data: {
        trackingNumber,
        declarationNumber: `DEC-OK-${Date.now()}`,
        declaredAt: `${utcDate(0)}T00:00:00Z`,
        remarks: '引取まで通す',
      },
    })
    expect(declared.status(), `申告を登録できない: ${await declared.text()}`).toBe(201)

    const cleared = await request.put(
      `/api/v1/customs/${(await declared.json()).declarationId}/status`,
      { headers: tracker, data: { status: 'CLEARED', reason: '審査完了' } },
    )
    expect(cleared.status(), `通関済にできない: ${await cleared.text()}`).toBe(200)

    const claimed = await request.post('/api/v1/handling', {
      headers: handler,
      data: {
        trackingNumber,
        type: 'CLAIM',
        locationUnLocode: 'USLAX',
        completionTime: `${utcDate(0)}T04:00:00Z`,
        consigneeConfirmation: '山田太郎（受取担当）',
      },
    })
    expect(claimed.ok(), `通関済なのに引取が通らない: ${await claimed.text()}`).toBeTruthy()
    expect((await claimed.json()).consigneeConfirmation).toContain('山田太郎')
  })

  /**
   * US30-1。**画面にキャンセル申請の入口が出る。**
   *
   * <p>API を直接叩く受け入れテストでは見つからない。画面は応答の
   * {@code availableActions} を見てボタンを出すため、<strong>サーバが操作を載せ忘れると
   * 画面には何も出ない</strong>——それでも API は 201 を返すので、API のテストは緑になる。
   * IT9 のクローズまで実際にその状態で、モックだけがこの操作を返していた。
   */
  test('輸送中の予約には、営業の画面にキャンセル申請の入口が出る', async ({
    page,
    request,
  }) => {
    const { trackingNumber, voyageNumber, bookingId } = await ensureTrackedVoyage(request)
    const handler = await handlerHeaders(request)

    for (const type of ['RECEIVE', 'LOAD']) {
      const recorded = await request.post('/api/v1/handling', {
        headers: handler,
        data: {
          trackingNumber,
          type,
          locationUnLocode: 'JPTYO',
          completionTime: `${utcDate(0)}T0${type === 'RECEIVE' ? '5' : '6'}:00:00Z`,
          voyageNumber: type === 'LOAD' ? voyageNumber : null,
        },
      })
      expect(recorded.ok(), `${type} を記録できない: ${await recorded.text()}`).toBeTruthy()
    }

    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('sales01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    // 積込が bookingms に届いて輸送中になるまで待つ
    await expect(async () => {
      await page.goto(`/booking/${bookingId}`)
      await expect(page.getByText('輸送中').first()).toBeVisible({ timeout: 3_000 })
    }).toPass({ timeout: 20_000 })

    await expect(
      page.getByRole('button', { name: 'キャンセルを申請する' }),
      '営業の画面にキャンセル申請の入口が無い。サーバが操作を載せていない',
    ).toBeVisible()
  })

  /**
   * 成功基準 3・4。輸送中 → 申請 → 陸揚げ地を指定して承認 → キャンセル確定。
   *
   * <p>輸送中にするのは<strong>荷役のイベント経由</strong>である。bookingms が初めて
   * 購読側に回った経路であり、交換機の引数が揃っていなければここで止まる。
   */
  test('輸送中のキャンセル申請と承認を実バックエンドで通す', async ({ request }) => {
    const { trackingNumber, voyageNumber, bookingId } = await ensureTrackedVoyage(request)
    const handler = await handlerHeaders(request)
    const tracker = await trackerHeaders(request)
    const { headers: sales } = await salesApi(request)

    for (const type of ['RECEIVE', 'LOAD']) {
      const recorded = await request.post('/api/v1/handling', {
        headers: handler,
        data: {
          trackingNumber,
          type,
          locationUnLocode: 'JPTYO',
          completionTime: `${utcDate(0)}T0${type === 'RECEIVE' ? '0' : '2'}:00:00Z`,
          // 積込は船に載せる作業なので航海番号が要る
          voyageNumber: type === 'LOAD' ? voyageNumber : null,
        },
      })
      expect(recorded.ok(), `${type} を記録できない: ${await recorded.text()}`).toBeTruthy()
    }

    // 積込が届いて初めて「輸送中」になる。届かなければ申請そのものが断られる
    await eventually(async () => {
      const requested = await request.post(
        `/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellation`,
        { headers: sales, data: { reason: '荷主都合による中止' } },
      )
      expect(requested.status(), '輸送中の予約でキャンセルを申請できない').toBe(201)
    })

    const pending = await request.get('/api/v1/cancellations', { headers: tracker })
    expect(pending.ok()).toBeTruthy()
    const target = (await pending.json()).find(
      (row: { bookingId: string }) => row.bookingId === bookingId,
    )
    expect(target, '承認待ちの一覧に出ていない').toBeTruthy()
    expect(
      target.dischargeCandidates.length,
      '陸揚げ地の候補が無い。指定して承認できない',
    ).toBeGreaterThan(0)

    const approved = await request.put(
      `/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellation/approve`,
      {
        headers: tracker,
        data: {
          dischargeLocationUnLocode: target.dischargeCandidates[0].unLocode,
          decisionReason: '現在地の港で陸揚げする',
        },
      },
    )
    expect(approved.status(), '承認できない').toBe(200)

    const cargo = await request.get(`/api/v1/bookings/${encodeURIComponent(bookingId)}`, {
      headers: sales,
    })
    expect(cargo.ok()).toBeTruthy()
    expect((await cargo.json()).bookingStatus, '承認したのに予約が取消になっていない')
      .toBe('CANCELLED')
  })
})

/**
 * IT10 Phase 6。**誤配を実環境で 1 本通す**（成功基準 1・2・3）。
 *
 * <p>ここでしか出ない食い違いがある。イベントは 2 つのサービスへ配られ、
 * <strong>片方だけ処理される形は例外にならない</strong>——予約は誤配になったのに
 * 例外が起票されない（またはその逆）が、どちらも黙って起きる。
 *
 * <p><strong>先にイメージを作り直すこと。</strong>同じタグのまま古いイメージが動いていると、
 * 直したはずの経路が緑になる。
 */
test.describe('IT10 実環境（誤配の検知と再設計）', () => {
  async function eventually(check: () => Promise<void>): Promise<void> {
    await expect.poll(async () => {
      try {
        await check()
        return 'ok'
      } catch {
        return 'not yet'
      }
    }, { timeout: 20_000, intervals: [500, 1000, 2000] }).toBe('ok')
  }

  async function trackerHeaders(request: APIRequestContext): Promise<{ Authorization: string }> {
    const tracker = await request.post('/api/v1/auth/login', {
      data: { userId: 'tracker01', password: 'password' },
    })
    expect(tracker.ok()).toBeTruthy()
    return { Authorization: `Bearer ${(await tracker.json()).token}` }
  }

  /**
   * 成功基準 1・2。予定外の荷役 → 予約が誤配 → 例外が起票 → 予約詳細に出る。
   *
   * <p><strong>1 つのイベントが 2 つのサービスへ届くことを、1 本で確かめる。</strong>
   * 片方だけの検査に分けると、もう片方が落ちても緑になる。
   */
  test('予定ルート外の荷役が、予約と追跡の両方に届く', async ({ request }) => {
    const { trackingNumber, voyageNumber, bookingId } = await ensureTrackedVoyage(request)
    const handler = await handlerHeaders(request)
    const tracker = await trackerHeaders(request)

    // 受領 → 積込（予定どおり）
    for (const type of ['RECEIVE', 'LOAD']) {
      const recorded = await request.post('/api/v1/handling', {
        headers: handler,
        data: {
          trackingNumber,
          type,
          locationUnLocode: 'JPTYO',
          completionTime: `${utcDate(0)}T0${type === 'RECEIVE' ? '7' : '8'}:00:00Z`,
          voyageNumber: type === 'LOAD' ? voyageNumber : null,
        },
      })
      expect(recorded.ok(), `${type} を記録できない: ${await recorded.text()}`).toBeTruthy()
    }

    // **予定にない港で荷降し**（旅程は JPTYO → USLAX なので、SGSIN は予定外）
    const misrouted = await request.post('/api/v1/handling', {
      headers: handler,
      data: {
        trackingNumber,
        type: 'UNLOAD',
        locationUnLocode: 'SGSIN',
        completionTime: `${utcDate(0)}T09:00:00Z`,
        voyageNumber,
      },
    })
    expect(misrouted.ok(), `予定外の荷役を記録できない: ${await misrouted.text()}`).toBeTruthy()
    expect((await misrouted.json()).offRoute, '予定外と判定されていない').toBe(true)

    // **予約が誤配になる**（成功基準 1 の片方）
    const { headers: sales } = await salesApi(request)
    await eventually(async () => {
      const cargo = await request.get(`/api/v1/bookings/${encodeURIComponent(bookingId)}`, {
        headers: sales,
      })
      expect(cargo.ok()).toBeTruthy()
      const body = await cargo.json()
      expect(body.routingStatus, '予約が誤配になっていない').toBe('MISROUTED')
      // **どこで外れたかまで届く**（US28-3）
      expect(body.misroute?.locationUnLocode, '外れた場所が届いていない').toBe('SGSIN')
      // **経路設計者に組み直しの操作が出る**（US28-4）
      expect(body.availableActions, '再設計の操作が載っていない').toContain('REASSIGN_ROUTE')
    })

    // **例外が自動起票される**（成功基準 2）
    await eventually(async () => {
      const open = await request.get('/api/v1/tracking/manage/exceptions', {
        headers: tracker,
      })
      expect(open.ok()).toBeTruthy()
      const text = await open.text()
      expect(text, '誤配の例外が起票されていない').toContain(trackingNumber)
      expect(text).toContain('MISROUTE')
      expect(text, '外れた場所が発生状況に入っていない').toContain('SGSIN')
    })
  })

  /**
   * 成功基準 3。現在地を出発地として組み直す。
   *
   * <p><strong>確定した記録を消さない</strong>（[ADR-026] 決定 4b）。輸送中の貨物が
   * 「経路を提示した」状態へ戻ると、荷主が合意した記録が消える。
   */
  test('誤配のあと、現在地から経路を組み直せる', async ({ request }) => {
    const { trackingNumber, voyageNumber, bookingId } = await ensureTrackedVoyage(request)
    const handler = await handlerHeaders(request)
    const { headers: sales } = await salesApi(request)

    for (const type of ['RECEIVE', 'LOAD']) {
      await request.post('/api/v1/handling', {
        headers: handler,
        data: {
          trackingNumber,
          type,
          locationUnLocode: 'JPTYO',
          completionTime: `${utcDate(0)}T1${type === 'RECEIVE' ? '0' : '1'}:00:00Z`,
          voyageNumber: type === 'LOAD' ? voyageNumber : null,
        },
      })
    }
    await request.post('/api/v1/handling', {
      headers: handler,
      data: {
        trackingNumber,
        type: 'UNLOAD',
        locationUnLocode: 'SGSIN',
        completionTime: `${utcDate(0)}T12:00:00Z`,
        voyageNumber,
      },
    })

    await eventually(async () => {
      const cargo = await request.get(`/api/v1/bookings/${encodeURIComponent(bookingId)}`, {
        headers: sales,
      })
      expect((await cargo.json()).routingStatus).toBe('MISROUTED')
    })

    // 現在地（SGSIN）から目的地（USLAX）への航海を用意する
    const routing = await request.post('/api/v1/auth/login', {
      data: { userId: 'routing01', password: 'password' },
    })
    const routingHeaders = { Authorization: `Bearer ${(await routing.json()).token}` }
    const registered = await request.post('/api/v1/voyages', {
      headers: routingHeaders,
      data: {
        voyageNumber: `V-RE-${Date.now()}`,
        vesselName: '組み直し丸',
        carrierName: '実 E2E 海運',
        supportedCargoTypes: ['GENERAL'],
        movements: [
          {
            departureUnLocode: 'SGSIN',
            arrivalUnLocode: 'USLAX',
            departureTime: `${utcDate(2)}T09:00:00Z`,
            arrivalTime: `${utcDate(30)}T09:00:00Z`,
          },
        ],
      },
    })
    expect(registered.ok(), `航海を登録できない: ${await registered.text()}`).toBeTruthy()

    // **現在地を出発地として候補を引く**（US28-4）
    const routes = await request.get(
      `/api/v1/routes?origin=SGSIN&destination=USLAX`
        + `&deadline=${businessLocalDateTime(120, '00:00').slice(0, 10)}&cargoType=GENERAL`,
      { headers: routingHeaders },
    )
    expect(routes.ok()).toBeTruthy()
    const candidates = (await routes.json()).candidates as Array<{
      legs: Array<{
        voyageNumber: string
        fromUnLocode: string
        toUnLocode: string
        departureTime: string
        arrivalTime: string
      }>
    }>
    expect(candidates.length, '現在地からの候補が出ない').toBeGreaterThan(0)

    const reassigned = await request.put(`/api/v1/bookings/${bookingId}/route`, {
      headers: routingHeaders,
      data: {
        legs: candidates[0].legs.map((l) => ({
          voyageNumber: l.voyageNumber,
          loadUnLocode: l.fromUnLocode,
          unloadUnLocode: l.toUnLocode,
          loadTime: l.departureTime,
          unloadTime: l.arrivalTime,
        })),
        maxTransshipments: null,
      },
    })
    expect(reassigned.status(), `組み直せない: ${await reassigned.text()}`).toBe(200)
    const body = await reassigned.json()

    // **確定した記録を消さない**（[ADR-026] 決定 4b）
    expect(body.bookingStatus, '輸送中の貨物が経路提示へ戻っている').toBe('IN_TRANSIT')
    expect(body.routingStatus).toBe('ROUTED')
    // **誤配の事実は残る**（US28-8。料金調整の根拠）
    expect(body.misroute?.locationUnLocode, '組み直した瞬間に誤配の記録が消えた')
      .toBe('SGSIN')

    // **例外を解決しても残る**（US28-8・デモ項目 10）。組み直しと例外の解決は別の
    // 担当者が別の画面で行う。どちらか一方でも記録を消すと、料金調整の根拠が
    // 「誤配があったはずだが証拠がない」になる
    const tracker = await trackerHeaders(request)
    const listed = await request.get('/api/v1/tracking/manage/exceptions', { headers: tracker })
    const target = ((await listed.json()) as Array<{
      trackingNumber: string
      activeException: { id: number; exceptionType: string } | null
    }>).find((t) => t.trackingNumber === trackingNumber)
    expect(target?.activeException?.exceptionType, '誤配の例外が見つからない').toBe('MISROUTE')

    const resolved = await request.post(
      `/api/v1/tracking/manage/exceptions/${target!.activeException!.id}/resolve`,
      {
        headers: tracker,
        data: {
          trackingNumber,
          resolutionNotes: '現在地から経路を組み直した',
          newEstimatedArrival: null,
        },
      },
    )
    expect(resolved.status(), `例外を解決できない: ${await resolved.text()}`).toBe(200)

    const after = await request.get(`/api/v1/bookings/${encodeURIComponent(bookingId)}`, {
      headers: sales,
    })
    expect((await after.json()).misroute?.locationUnLocode, '例外を解決したら誤配の記録が消えた')
      .toBe('SGSIN')
  })

  /**
   * US28-4・US28-6（[ADR-026] 決定 4・5）。**期限を超える便しか無くても組み直せる。**
   *
   * <p>誤配した貨物は遅れているのが普通で、元の期限に間に合う便はまず残っていない。
   * routingms は既定で期限を超える候補を刈るため、**「弾かない」を伝えないと候補が
   * 1 本も返らず、貨物は経路から外れたまま止まる**——決定 4 が避けようとした事態そのもの。
   *
   * <p>集約から期限検査を外しただけでは、この経路には効かない。**bookingms →
   * routingms を実際に通して確かめる**（IT10 レビュー・architect 高 1）。
   */
  test('期限を超える便しか無くても組み直せて、何日超えるかが返る', async ({ request }) => {
    const { trackingNumber, voyageNumber, bookingId } = await ensureTrackedVoyage(request)
    const handler = await handlerHeaders(request)
    const { headers: sales } = await salesApi(request)
    const routing = await request.post('/api/v1/auth/login', {
      data: { userId: 'routing01', password: 'password' },
    })
    const routingHeaders = { Authorization: `Bearer ${(await routing.json()).token}` }

    for (const type of ['RECEIVE', 'LOAD']) {
      await request.post('/api/v1/handling', {
        headers: handler,
        data: {
          trackingNumber,
          type,
          locationUnLocode: 'JPTYO',
          completionTime: `${utcDate(0)}T1${type === 'RECEIVE' ? '0' : '1'}:00:00Z`,
          voyageNumber: type === 'LOAD' ? voyageNumber : null,
        },
      })
    }
    await request.post('/api/v1/handling', {
      headers: handler,
      data: {
        trackingNumber,
        type: 'UNLOAD',
        locationUnLocode: 'SGSIN',
        completionTime: `${utcDate(0)}T12:00:00Z`,
        voyageNumber,
      },
    })

    await eventually(async () => {
      const cargo = await request.get(`/api/v1/bookings/${encodeURIComponent(bookingId)}`, {
        headers: sales,
      })
      expect((await cargo.json()).routingStatus).toBe('MISROUTED')
    })

    // **期限（120 日後）より後にしか着かない便**を用意する。これしか無い状況が、
    // 誤配のあとの現実である
    const registered = await request.post('/api/v1/voyages', {
      headers: routingHeaders,
      data: {
        voyageNumber: `V-LATE-${Date.now()}`,
        vesselName: '遅れて着く丸',
        carrierName: '実 E2E 海運',
        supportedCargoTypes: ['GENERAL'],
        movements: [
          {
            departureUnLocode: 'SGSIN',
            arrivalUnLocode: 'USLAX',
            departureTime: `${utcDate(150)}T09:00:00Z`,
            arrivalTime: `${utcDate(200)}T09:00:00Z`,
          },
        ],
      },
    })
    expect(registered.ok(), `航海を登録できない: ${await registered.text()}`).toBeTruthy()

    const deadline = businessLocalDateTime(120, '00:00').slice(0, 10)
    // **弾かないことを伝える**。これが無いと候補は 0 件になる
    const routes = await request.get(
      `/api/v1/routes?origin=SGSIN&destination=USLAX`
        + `&deadline=${deadline}&cargoType=GENERAL&reroute=true`,
      { headers: routingHeaders },
    )
    expect(routes.ok()).toBeTruthy()
    const candidates = (await routes.json()).candidates as Array<{
      legs: Array<{
        voyageNumber: string
        fromUnLocode: string
        toUnLocode: string
        departureTime: string
        arrivalTime: string
      }>
    }>
    const late = candidates.find((candidate) =>
      candidate.legs.some((leg) => leg.voyageNumber.startsWith('V-LATE-')),
    )
    expect(late, '期限を超える候補が刈られている。誤配した貨物を組み直せない')
      .toBeDefined()

    // **伝えなければ返らない**ことも確かめる。判別しない検査にしない
    const enforced = await request.get(
      `/api/v1/routes?origin=SGSIN&destination=USLAX`
        + `&deadline=${deadline}&cargoType=GENERAL`,
      { headers: routingHeaders },
    )
    const enforcedCandidates = (await enforced.json()).candidates as Array<{
      legs: Array<{ voyageNumber: string }>
    }>
    expect(
      enforcedCandidates.some((candidate) =>
        candidate.legs.some((leg) => leg.voyageNumber.startsWith('V-LATE-')),
      ),
      '通常の探索まで期限で弾かなくなっている',
    ).toBeFalsy()

    const reassigned = await request.put(`/api/v1/bookings/${bookingId}/route`, {
      headers: routingHeaders,
      data: {
        legs: late!.legs.map((l) => ({
          voyageNumber: l.voyageNumber,
          loadUnLocode: l.fromUnLocode,
          unloadUnLocode: l.toUnLocode,
          loadTime: l.departureTime,
          unloadTime: l.arrivalTime,
        })),
        maxTransshipments: null,
      },
    })
    expect(reassigned.status(), `組み直せない: ${await reassigned.text()}`).toBe(200)
    const body = await reassigned.json()
    // **何日超えるかが返る**（US28-6）。荷主に伝えて判断してもらう材料である
    expect(body.daysBeyondDeadline, '超過の日数が返っていない。荷主に説明できない')
      .toBeGreaterThan(0)
    // **組み直したら、もう経路から外れてはいない**（決定 4b）
    expect(body.routingStatus).toBe('ROUTED')

    // **伝える側の営業も読める**（US28-6。通知は代替）
    const detail = await request.get(`/api/v1/bookings/${encodeURIComponent(bookingId)}`, {
      headers: sales,
    })
    expect(
      (await detail.json()).daysBeyondDeadline,
      '営業が予約詳細を開いても超過の日数が読めない',
    ).toBeGreaterThan(0)
  })
})

/**
 * 精算（US21・US22・IT11）。
 *
 * <p><strong>経理担当者が初めて仕事をする。</strong>ロールは IT1 から存在するが、
 * 開いている画面が 1 つも無い状態が 10 イテレーション続いていた。
 *
 * <p>ここで確かめるのは<strong>実物での 1 往復</strong>である——billingms が
 * bookingms を呼び、料金を算出し、精算書を発行するまで。MSW は仕様の写しであり、
 * 写し間違いはモックが緑のままでは分からない。
 */
test.describe('精算（実バックエンド）', () => {
  async function accountantHeaders(
    request: APIRequestContext,
  ): Promise<{ Authorization: string }> {
    const login = await request.post('/api/v1/auth/login', {
      data: { userId: 'accountant01', password: 'password' },
    })
    expect(login.ok()).toBeTruthy()
    return { Authorization: `Bearer ${(await login.json()).token}` }
  }

  /**
   * <strong>引取が終わっていない予約は断られる</strong>（[ADR-027] 決定 5）。
   *
   * <p>画面で出し分けるだけでは守れない——URL を直接開かれる。
   */
  test('引取が終わっていない予約の料金は、実物でも算出できない', async ({ request }) => {
    const bookingId = await ensureBookingWaitingForRouting(request)
    const headers = await accountantHeaders(request)

    const calculation = await request.get(
      `/api/v1/billing/calculations/${encodeURIComponent(bookingId)}`,
      { headers },
    )

    expect(
      calculation.status(),
      '運び終える前の予約に料金を出そうとして通っている',
    ).toBe(409)
  })

  /**
   * <strong>経理担当者以外は精算を扱えない。</strong>
   *
   * <p>請求の金額を決めるのは経理であり、営業とは職掌が違う。
   */
  test('営業担当者は、実物でも精算を扱えない', async ({ request }) => {
    const { headers } = await salesApi(request)

    const unbilled = await request.get('/api/v1/billing/unbilled', { headers })

    expect(unbilled.status(), '営業が精算を読めている').toBe(403)
  })

  /**
   * <strong>経理担当者が画面から精算管理へ辿り着ける</strong>（Try 5）。
   *
   * <p>画面単体のテストはルートガードを通らないため、リンクが存在することは
   * 確かめられても、<strong>押せることは確かめられない</strong>。
   */
  test('経理担当者は、ダッシュボードから精算管理へ辿り着ける', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('accountant01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL(/\/dashboard/)

    await expect(
      page.getByRole('heading', { name: '経理ダッシュボード' }),
      '経理担当者のダッシュボードが出ていない',
    ).toBeVisible()

    await page.getByRole('link', { name: '料金を算出する' }).click()

    await expect(page, '精算管理を開けていない').toHaveURL(/\/billing/)
    await expect(page.getByRole('heading', { name: '精算管理' })).toBeVisible()
  })

  /**
   * <strong>料金算出の入力が bookingms から届く</strong>（[ADR-027] 決定 7）。
   *
   * <p>ACL が実物で往復することを確かめる。契約テストは<strong>両側が同じ形を
   * 期待していること</strong>までしか見ない——実際に繋がるかは通してみないと分からない
   * （IT5 は名乗りを忘れ、実環境の往復を通すまで誰も気づかなかった）。
   */
  test('料金未算出の一覧が、実物の bookingms から届く', async ({ request }) => {
    const headers = await accountantHeaders(request)

    const unbilled = await request.get('/api/v1/billing/unbilled', { headers })

    expect(
      unbilled.status(),
      `bookingms への往復が通っていない: ${await unbilled.text()}`,
    ).toBe(200)
    expect(Array.isArray(await unbilled.json())).toBeTruthy()
  })
})
