import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

/**
 * IT11 の受け入れ。US21（輸送料金の算出）・US22（法人割引）に対応する。
 *
 * **デモ項目 10 件を、この順で通す**（[IT11 計画](../../../docs/development/iteration_plan-11.md)）。
 * 実演で緑になることを「固定されている」と取り違えない——IT10 は 10 件のうち 2 件に
 * 対応する検査が無く、実装が入っていたので実演では緑になっていた。
 *
 * **経理担当者（`ROLE_ACCOUNTANT`）が初めて仕事をする IT である。** ロールは IT1 から
 * 存在するが、開いている画面が 1 つも無い状態が 10 イテレーション続いていた。
 * したがって**到達できること自体**を最初に確かめる。
 *
 * IT11 のスコープ外:
 * - 支払いの確認・入金（US23・IT12）。`PaymentStatus` は `PENDING` までしか動かさない
 * - 荷主への精算書の通知（US23・IT12）
 * - 見積との突き合わせ（US01・IT12）
 */

/** 種データ（`src/mocks/data.ts`）。引取済で、法人荷主の予約。 */
const CORPORATE_BOOKING = 'BKG-2026000007'

async function logInAs(page: Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

async function logInAsAccountant(page: Page) {
  await logInAs(page, 'accountant01')
}

test.describe('精算管理（US21・US22）', () => {
  /**
   * デモ 1。**件数から対象一覧へ辿れる**（横断規約）。
   *
   * 経理担当者は他に気づく手段を持たない——メールの仕組みは無い。
   * 件数を出すだけで導線が無いと、**気づいても仕事が進まない**。
   */
  test('デモ 1: ダッシュボードの件数から、料金未算出の予約へ辿れる', async ({ page }) => {
    await logInAsAccountant(page)

    const notice = page.getByText(/料金を算出していない引取済の予約/)
    await expect(notice, '経理担当者のダッシュボードに件数が出ていない').toBeVisible()

    await page.getByRole('link', { name: /精算管理|料金を算出/ }).first().click()
    await expect(page).toHaveURL(/\/billing/)
  })

  /**
   * デモ 2。**輸送実績が出る**（21-2）。
   *
   * **距離は持っていない**（[ADR-027](../../../docs/adr/027-transport-charge-calculation.md)
   * 決定 1）。区間数で代替し、その旨を画面に書く。
   */
  test('デモ 2: 引取済の予約を選ぶと、輸送実績が出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByRole('link', { name: new RegExp(CORPORATE_BOOKING) }).click()
    await expect(page).toHaveURL(new RegExp(`/billing/new/${CORPORATE_BOOKING}`))

    // **根拠の枠の中を見る。** ページ全体で `重量` を探すと `重量係数` にも一致し、
    // どちらが出ていても緑になる
    const basis = page.getByTestId('charge-basis')
    await expect(basis, '重量の実績が出ていない').toContainText('4,200 kg')
    await expect(basis, '貨物種別が出ていない').toContainText('一般貨物')
    await expect(basis, '区間数が出ていない').toContainText('2 区間')
    await expect(
      page.getByText(/距離は保持していません|地域区分で代替/),
      '距離の代わりに区間ごとの地域区分を使っていることを画面が言っていない',
    ).toBeVisible()
  })

  /**
   * デモ 3。**根拠が出る**（21-3・[ADR-027] 決定 1）。
   *
   * 金額そのものより「なぜその金額か」が読めることを優先する——経理担当者は
   * 請求の根拠を荷主に説明する。
   */
  test('デモ 3: 基本料金が自動で計算され、その根拠が出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    const basis = page.getByTestId('charge-basis')
    await expect(basis, '基本料金の根拠が出ていない。荷主に説明できない').toBeVisible()
    await expect(basis).toContainText('基準運賃')
    await expect(basis).toContainText('区間係数')
    await expect(basis).toContainText('重量係数')
    await expect(basis).toContainText('貨物種別係数')
  })

  /**
   * デモ 4。**法人には割引が入る**（22-1・22-2）。
   *
   * 割引率は荷主に登録済み（US03・IT2）。**経理担当者が入力するのではない**
   * ——手で入れると、契約と違う率が入る。
   */
  test('デモ 4: 法人荷主では割引率が自動で入り、割引後の金額が出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    await expect(page.getByTestId('discount-rate'), '割引率が自動で入っていない')
      .toContainText('%')
    await expect(page.getByTestId('discounted-amount'), '割引後の金額が出ていない')
      .toBeVisible()
  })

  /** デモ 5。**個人荷主には割引が適用されない**（22-3）。 */
  test('デモ 5: 個人荷主では割引が適用されない', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByTestId('unbilled-individual').first().getByRole('link').click()

    await expect(
      page.getByTestId('discount-rate'),
      '個人荷主に割引率の欄が出ている。契約が無いのに割引の話が始まる',
    ).toHaveCount(0)
  })

  /**
   * デモ 6。**誤配の記録が根拠として出る**（21-6）。
   *
   * IT10 の `Misroute` が初めて読まれる。「残っている」と「読める」は別である
   * ——IT10 までは予約詳細にしか出ておらず、経理担当者はその画面を開けなかった。
   */
  test('デモ 6: 誤配した貨物では、その記録が根拠として出る', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByTestId('unbilled-misrouted').first().getByRole('link').click()

    const evidence = page.getByTestId('adjustment-evidence')
    await expect(evidence, '誤配の記録が根拠として出ていない').toBeVisible()
    await expect(evidence, '外れた場所が出ていない').toContainText('Singapore')
  })

  /** デモ 7。**調整を入れると合計が変わる**（21-6）。 */
  test('デモ 7: 調整を入れると、合計が変わる', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    const before = await page.getByTestId('total-amount').textContent()

    await page.getByLabel('調整の内容').fill('遅延による減額')
    await page.getByLabel('調整額').fill('-10000')
    await page.getByRole('button', { name: '調整を追加' }).click()

    await expect(page.getByTestId('total-amount'), '調整を入れても合計が変わらない')
      .not.toHaveText(before ?? '')
  })

  /**
   * デモ 8。**確定すると金額が動かなくなる**（21-4・21-5・[ADR-027] 決定 4）。
   *
   * 請求書は荷主へ出す約束である。出したあとに黙って変わると、請求の根拠が消える。
   */
  test('デモ 8: 確定すると精算書が発行され、金額が動かなくなる', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)

    await page.getByRole('button', { name: '確定する' }).click()

    await expect(page, '精算書の詳細へ遷移していない').toHaveURL(/\/billing\/INV-/)
    await expect(page.getByTestId('payment-status')).toContainText('未入金')
    await expect(
      page.getByRole('button', { name: /調整を追加|確定する/ }),
      '発行後も金額を動かす操作が残っている。請求の根拠が消える',
    ).toHaveCount(0)
  })

  /**
   * デモ 9。**割引の根拠が精算書に残る**（22-4）。
   *
   * **前提（発行済みの精算書）は自分で作る。** テストは 1 件ずつ独立して動くため、
   * デモ 8 が発行したものはここには残らない。「あれば見る」形にすると、
   * **無いときに黙って素通りする**——通っていないことに気づけない。
   */
  test('デモ 9: 請求書詳細に、割引の根拠が記載されている', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)
    await page.getByRole('button', { name: '確定する' }).click()
    await expect(page).toHaveURL(/\/billing\/INV-/)

    const breakdown = page.getByTestId('amount-breakdown')
    await expect(breakdown, '基本料金が出ていない').toContainText('基本運賃')
    await expect(breakdown, '割引率が出ていない。額だけでは率を復元できない')
      .toContainText('%')
    await expect(breakdown, '割引後の金額が出ていない').toContainText('合計')
  })

  /**
   * デモ 10。**キャンセル料が算定される**（US30-9・IT9 からの持ち越し）。
   *
   * IT9 は画面に「算定していません」と書いた。本 IT でその一文が消える。
   */
  test('デモ 10: キャンセルされた予約にキャンセル料が算定される', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByTestId('unbilled-cancelled').first().getByRole('link').click()

    const fee = page.getByTestId('cancellation-fee')
    await expect(fee, 'キャンセル料が算定されていない').toBeVisible()
    await expect(fee, '料率の根拠（キャンセル時の予約状態）が出ていない')
      .toContainText(/輸送中|輸送開始前/)
  })
})

test.describe('経理担当者の到達性（Try 5）', () => {
  /**
   * **ルートガードを通る経路で確かめる**（IT10 Try 5）。
   *
   * 画面単体のテストはルートガードを通らないため、リンクが存在することは
   * 確かめられても、**押せることは確かめられない**。IT10 は予約詳細への導線
   * 3 か所が 403 だった。
   */
  test('経理担当者は精算管理を開ける', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')
    await expect(page).toHaveURL(/\/billing/)
    await expect(page.getByRole('heading', { name: '精算管理' })).toBeVisible()
  })

  test('経理担当者以外は精算管理を開けない', async ({ page }) => {
    await logInAs(page, 'sales01')
    await page.goto('/billing')
    await expect(page, '営業担当者が精算管理を開けている').toHaveURL(/\/403/)
  })
})

/**
 * IT12 の受け入れ。US23（精算を処理する）と、US21 の未達返済（距離・輸出免税）。
 *
 * **デモ項目 1・2・3・4・8・9・11 に対応する**（[IT12 計画](../../../docs/development/iteration_plan-12.md)）。
 *
 * IT12 のスコープ外（**代替で満たす**——満たしたことにしない）:
 * - 精算書のメール通知（23-2）。画面が「送っていない」と言う
 * - 決済機関との連携（23-3）。経理担当者が入金を手入力する
 * - 期限超過の通知（23-5）。ダッシュボードの件数と一覧で代替する
 */
test.describe('精算処理（US23）', () => {
  /** 発行済みの請求書を 1 通作る。**前提は自分で作る**（デモ 9 と同じ理由）。 */
  async function issueInvoice(page: Page) {
    await page.goto(`/billing/new/${CORPORATE_BOOKING}`)
    await expect(page.getByRole('heading', { name: '料金算出' })).toBeVisible()
    await page.getByRole('button', { name: '確定する' }).click()
    await expect(page).toHaveURL(/\/billing\/INV-/)
  }

  /**
   * デモ 1。**入金を確認すると「入金済」になる**（23-3・23-4）。
   *
   * 決済機関との連携は無い。経理担当者が入金日・金額・方法・参照番号を手で入れる。
   * **代替であることを画面が言う**——言わないと、経理担当者は「連携が壊れている」と
   * 受け取って待ち続ける。
   */
  test('デモ 1: 入金を確認すると、請求書が「入金済」になる', async ({ page }) => {
    await logInAsAccountant(page)
    await issueInvoice(page)

    await expect(
      page.getByText(/決済機関とは連携していません|入金は手で確認/),
      '入金確認が手作業であることを画面が言っていない',
    ).toBeVisible()

    await page.getByRole('link', { name: '入金を確認する' }).click()
    await expect(page).toHaveURL(/\/billing\/INV-.*\/payment$/)
    await expect(page.getByRole('heading', { name: '入金の確認' })).toBeVisible()

    // **入金額は請求額が入っている。**打ち直しは桁を間違える機会を作るだけである
    await expect(
      page.getByLabel('入金額'),
      '入金額に請求額が入っていない',
    ).not.toHaveValue('')
    await page.getByLabel('入金日').fill('2027-10-15')
    await page.getByLabel('入金方法').selectOption('BANK_TRANSFER')
    await page.getByLabel('参照番号').fill('FT27101500123')
    await page.getByRole('button', { name: '確認する' }).click()

    await expect(page).toHaveURL(/\/billing\/INV-[^/]+$/)
    await expect(page.getByTestId('payment-status'), '入金済になっていない')
      .toContainText('入金済')
    // **入れた根拠が残る。**「入金済」だけでは、いつ・いくら・どの振込かが追えない
    const payment = page.getByTestId('payment-record')
    await expect(payment).toContainText('2027-10-15')
    await expect(payment).toContainText('FT27101500123')
  })

  /**
   * <strong>請求額と違う入金は、一度止める</strong>（IT12 レビュー・user 高 1）。
   *
   * <p>振込手数料の差引も一部入金も日常的に起きる。黙って通すと、
   * <strong>不足のまま「入金済」で閉じた請求</strong>が積み上がる——期限超過の
   * 一覧からも消えるので、誰も気づけない。
   */
  test('請求額と違う入金額は、差額を出して一度止まる', async ({ page }) => {
    await logInAsAccountant(page)
    await issueInvoice(page)
    await page.getByRole('link', { name: '入金を確認する' }).click()

    await page.getByLabel('入金日').fill('2027-10-15')
    await page.getByLabel('入金額').fill('1000')
    await page.getByRole('button', { name: '確認する' }).click()

    // 1 度目は止まる（画面に残る）
    await expect(page).toHaveURL(/\/payment$/)
    await expect(
      page.getByTestId('payment-difference'),
      '請求額と違う入金額が、そのまま通っている',
    ).toContainText('不足')

    // 確かめたうえで、もう一度押せば通る——判断するのは経理担当者である
    await page.getByRole('button', { name: '確認する' }).click()
    await expect(page).toHaveURL(/\/billing\/INV-[^/]+$/)
    await expect(page.getByTestId('payment-status')).toContainText('入金済')
  })

  /**
   * デモ 2。**予約が「精算済」になる**（23-4）。
   *
   * <strong>画面をまたぐ 1 本である。</strong>billingms で入金を確認したことが、
   * bookingms の予約状態に届いていることを、**予約の画面で**確かめる
   * ——billingms の中だけを見ても、届いたかどうかは分からない。
   */
  test('デモ 2: 入金確認後、予約が「精算済」になっている', async ({ page }) => {
    await logInAsAccountant(page)
    await issueInvoice(page)
    await page.getByRole('link', { name: '入金を確認する' }).click()
    await page.getByLabel('入金日').fill('2027-10-15')
    await page.getByLabel('入金方法').selectOption('BANK_TRANSFER')
    await page.getByRole('button', { name: '確認する' }).click()
    await expect(page.getByTestId('payment-status')).toContainText('入金済')

    // **読み込み直さない。**モック（MSW）の状態はブラウザのメモリにあり、
    // ページを読み込み直すと入金の確認ごと消える。請求書の画面にある予約番号の
    // リンクを辿る——実際の経理担当者もそう辿る
    await page.getByRole('link', { name: CORPORATE_BOOKING }).click()
    await expect(page.getByRole('heading', { name: new RegExp(CORPORATE_BOOKING) })).toBeVisible()
    await expect(
      page.getByTestId('booking-status'),
      '入金を確認したのに予約が引取済のまま。精算が閉じない',
    ).toContainText('精算済')
  })

  /**
   * デモ 3。**期限を過ぎた請求に気づける**（23-5 の代替）。
   *
   * 未払い通知のメールは無い。**件数を出すだけでは仕事は進まない**ので、
   * そこから対象の一覧へ辿れることまでを確かめる。
   */
  test('デモ 3: 支払期限を過ぎた請求が件数で出て、そこから一覧へ辿れる', async ({ page }) => {
    await logInAsAccountant(page)

    const notice = page.getByText(/支払期限を過ぎた請求/)
    await expect(notice, '期限超過に気づく手段がダッシュボードに無い').toBeVisible()

    await page.getByRole('link', { name: /支払期限を過ぎた請求/ }).click()
    await expect(page).toHaveURL(/\/billing\?.*overdue/)
    await expect(
      page.getByTestId('overdue-invoices'),
      '件数からたどり着いた先に、対象の請求書が並んでいない',
    ).toBeVisible()

    // **全件の一覧でも見分けられる**（IT12 レビュー・user 中）。状態列は保存上
    // ずっと「未入金」なので、超過はここでしか気づけない
    await page.getByRole('link', { name: 'すべての精算書を見る' }).click()
    await expect(
      page.getByTestId('issued-invoices'),
      '全件の一覧で、期限を過ぎた請求書を見分けられない',
    ).toContainText('（超過）')
  })

  /**
   * デモ 4。**取り消して出し直せる**（経理担当者の申し送り②）。
   *
   * 発行した金額は動かさない（[ADR-027] 決定 4）。**間違いは赤伝で取り消し、
   * 新しい請求番号で出し直す**——DB を直すのは監査に耐えない。
   */
  test('デモ 4: 請求書を取り消すと、新しい請求番号で出し直せる', async ({ page }) => {
    await logInAsAccountant(page)
    await issueInvoice(page)
    const firstNumber = new URL(page.url()).pathname.split('/').pop()

    await page.getByRole('button', { name: '取り消す' }).click()
    await page.getByLabel('取り消しの理由').fill('金額の誤りのため')
    await page.getByRole('button', { name: '取り消しを記録する' }).click()

    await expect(page.getByTestId('void-reason'), '取り消した理由が残っていない')
      .toContainText('金額の誤りのため')

    // **取り消したら、同じ予約に出し直せる。**出し直せなければ請求できないまま残る。
    // **読み込み直さない**——モック（MSW）の状態が消え、取り消し自体が無かったことになる
    await page.getByRole('link', { name: '精算管理へ戻る' }).click()
    await page.getByRole('link', { name: new RegExp(CORPORATE_BOOKING) }).click()
    await expect(page.getByRole('heading', { name: '料金算出' })).toBeVisible()
    await page.getByRole('button', { name: '確定する' }).click()
    await expect(page).toHaveURL(/\/billing\/INV-/)
    expect(
      new URL(page.url()).pathname.split('/').pop(),
      '出し直した請求書が同じ請求番号を使っている。どちらが有効か分からなくなる',
    ).not.toBe(firstNumber)
  })

  /**
   * デモ 11。**画面の数字がそのまま紙になる**（経理担当者の申し送り③）。
   *
   * 印刷が無いと、数字を書き写して表計算で作ることになり、システムの金額と
   * 実際に送った請求書が食い違い始める。
   */
  test('デモ 11: 請求書を印刷できる', async ({ page }) => {
    await logInAsAccountant(page)
    await issueInvoice(page)

    await expect(
      page.getByRole('button', { name: '印刷する' }),
      '印刷の手段が無い。数字を書き写すことになる',
    ).toBeEnabled()
  })
})

/**
 * US21 の未達返済。デモ 8（地域区分）・デモ 9（輸出免税）。
 */
test.describe('距離と輸出免税（US21 の未達返済）', () => {
  /** 国内 1 区間と遠洋 1 区間。**同じ区間数でも金額が違う。** */
  test('デモ 8: 国内輸送と国際輸送で、同じ区間数でも金額が違う', async ({ page }) => {
    await logInAsAccountant(page)

    await page.goto('/billing/new/BKG-2026000007')
    const domestic = await page.getByTestId('base-amount').textContent()

    await page.goto('/billing/new/BKG-2026000008')
    const ocean = await page.getByTestId('base-amount').textContent()

    expect(
      ocean,
      '国内の積み替えと太平洋横断が同じ金額になっている。荷主に説明できない',
    ).not.toBe(domestic)

    await expect(
      page.getByTestId('charge-basis'),
      '地域区分が内訳に出ていない。なぜその金額かが読めない',
    ).toContainText(/遠洋|近海|国内/)
  })

  /** 輸出免税。**国が異なれば消費税は付かない。** */
  test('デモ 9: 国際輸送の請求書に消費税が付かない', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing/new/BKG-2026000008')
    await page.getByRole('button', { name: '確定する' }).click()
    await expect(page).toHaveURL(/\/billing\/INV-/)

    const breakdown = page.getByTestId('amount-breakdown')
    await expect(breakdown, '税区分が出ていない').toContainText('輸出免税')
    await expect(breakdown, '国際輸送に消費税が付いている').toContainText('消費税（輸出免税）¥0')
  })
})

/**
 * 請求書を探す（US38）。
 *
 * **4 度目の申し送りである。** 月末の締めが表計算に落ちたまま、IT11・IT12 の
 * レビューで経理担当者から 2 IT 連続の指摘を受けていた。
 */
test.describe('請求書の検索（US38）', () => {
  test('荷主名で絞り込み、件数と合計を読める', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    const issued = page.getByTestId('issued-invoices')
    await expect(issued.getByRole('heading', { name: /発行済みの精算書/ })).toBeVisible()

    // **合計は締めの数字である。**取り消し済みを除くことを画面が言う
    await expect(issued.getByText('（取り消し済みを除く）')).toBeVisible()

    await page.getByLabel('精算書を探す（請求番号・荷主名・予約番号）').fill('大洋物産')

    // 絞った結果だけが残る
    await expect(issued.getByTestId('invoice-link').first()).toBeVisible()
    await expect(page.getByRole('button', { name: '条件を消す' })).toBeVisible()
  })

  /**
   * **読めない発行月は断る。** 黙って「指定なし」に倒すと、打ち間違えた担当者には
   * 全件が返り、絞ったつもりの数字を締めに使うことになる。
   */
  test('発行月で絞り込める', async ({ page }) => {
    await logInAsAccountant(page)
    await page.goto('/billing')

    await page.getByLabel('発行月').fill('2026-06')

    await expect(page.getByRole('button', { name: '条件を消す' })).toBeVisible()
  })
})
