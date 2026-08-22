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
