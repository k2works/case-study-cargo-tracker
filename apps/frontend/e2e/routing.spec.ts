import { expect, test } from '@playwright/test'
import { businessLocalDateTime } from './support/business-time.js'

/**
 * IT3・IT4・IT5 の受け入れ。US24（航海登録）・US25（航海更新）・US07（航海検索）・
 * US06（引き渡し）・US08（経路候補算出）・US09（経路の選択と確定）・US10（条件の調整）・
 * US11（予約への反映）。
 *
 * スコープ外（依存先が未実装、または設計にモデルが無い）:
 * - 港湾制約による絞り込み（ADR-018 で「持たない」と決めた）
 * - 経路設計依頼のメール通知（US06 / US10。画面上の気づく手段で代替）
 * - 予約の訂正（US06。次イテレーション以降）
 * - CargoRoutedEvent の発行（ADR-019 決定 3。イベント基盤は IT6）
 * - **利用者を切り替える往復**（モックは再読み込みで初期化されるため、実バックエンドの
 *   検査（real-backend.spec.ts）が受け持つ）
 */

async function logIn(page: import('@playwright/test').Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

/** 航海番号は実行ごとに変える。同じ番号を使い回すと、2 回目から差分確認に入る。 */
function voyageNumber(suffix: string): string {
  return `E2E-${suffix}-${process.env.E2E_RUN_ID ?? 'local'}`
}

test.describe('航海スケジュールの登録（US24）', () => {
  test('経路設計者は航海を登録でき、一覧で確認できる', async ({ page }) => {
    const number = voyageNumber('A')
    await logIn(page, 'routing01')

    await page.goto('/routing/voyages')
    await page.getByRole('link', { name: '航海を登録する' }).click()

    await page.getByLabel('航海番号').fill(number)
    await page.getByLabel('船名').fill('さくら丸')
    await page.getByLabel('運送会社').fill('日本郵船')
    await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
    await page.getByLabel('1 区間目の到着地').selectOption('USLAX')
    // 日時は業務タイムゾーンで作る。toISOString を直に使うと CI（UTC）で 1 日ずれる
    await page.getByLabel('1 区間目の出発日時').fill(businessLocalDateTime(30, '09:00'))
    await page.getByLabel('1 区間目の到着日時').fill(businessLocalDateTime(47, '12:00'))
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByText(`航海 ${number} を登録しました`)).toBeVisible()

    await page.getByRole('button', { name: '一覧で確認する' }).click()
    await expect(page.getByRole('cell', { name: number })).toBeVisible()
    // どの船かが分からないと、荷役と問い合わせで貨物を追えない
    await expect(page.getByRole('cell', { name: 'さくら丸' }).first()).toBeVisible()
  })

  test('未入力は画面のメッセージで示す（吹き出しだけで終わらせない）', async ({ page }) => {
    await logIn(page, 'routing01')
    await page.goto('/routing/voyages/new')

    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByRole('alert')).toContainText('航海番号は必須です')
  })

  test('営業担当者は航海スケジュールを開けない', async ({ page }) => {
    await logIn(page, 'sales01')

    await page.goto('/routing/voyages')

    await expect(page).toHaveURL(/\/403/)
  })
})

test.describe('航海スケジュールの更新（US25）', () => {
  test('同じ航海番号なら差分を見せ、上書きを選ばせる', async ({ page }) => {
    const number = voyageNumber('B')
    await logIn(page, 'routing01')
    await page.goto('/routing/voyages/new')

    // 画面のリンクで移動する。ページを読み直すと、モックが持つ登録済みの航海が消えて
    // 「2 回目の登録」が新規登録になり、差分確認に入らない
    async function submit(vesselName: string) {
      await page.getByLabel('航海番号').fill(number)
      await page.getByLabel('船名').fill(vesselName)
      await page.getByLabel('運送会社').fill('日本郵船')
      await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
      await page.getByLabel('1 区間目の到着地').selectOption('SGSIN')
      await page.getByLabel('1 区間目の出発日時').fill(businessLocalDateTime(30, '09:00'))
      await page.getByLabel('1 区間目の到着日時').fill(businessLocalDateTime(33, '12:00'))
      await page.getByRole('button', { name: '登録する' }).click()
    }

    await submit('さくら丸')
    await expect(page.getByText(`航海 ${number} を登録しました`)).toBeVisible()

    await submit('つばき丸')
    // 何が変わるか分からないまま押させない
    await expect(page.getByText('既に登録されています')).toBeVisible()
    await expect(page.getByRole('cell', { name: '船名' })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'さくら丸' })).toBeVisible()

    await page.getByRole('button', { name: 'この内容で上書きする' }).click()
    await expect(page.getByText(`航海 ${number} を登録しました`)).toBeVisible()

    await page.getByRole('button', { name: '一覧で確認する' }).click()
    await expect(page.getByRole('cell', { name: 'つばき丸' }).first()).toBeVisible()
  })

  /**
   * 一覧の「更新する」から入る、実際の更新の経路。
   *
   * 10 区間ある航海の到着を 1 日ずらすために全部打ち直させると、その過程で別の項目が変わる。
   */
  test('一覧の「更新する」は今の内容を引き継ぎ、直したいところだけ直せる', async ({ page }) => {
    const number = voyageNumber('E')
    await logIn(page, 'routing01')
    await page.goto('/routing/voyages/new')

    await page.getByLabel('航海番号').fill(number)
    await page.getByLabel('船名').fill('かえで丸')
    await page.getByLabel('運送会社').fill('商船三井')
    await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
    await page.getByLabel('1 区間目の到着地').selectOption('SGSIN')
    await page.getByLabel('1 区間目の出発日時').fill(businessLocalDateTime(30, '09:00'))
    await page.getByLabel('1 区間目の到着日時').fill(businessLocalDateTime(33, '12:00'))
    await page.getByRole('button', { name: '登録する' }).click()
    await page.getByRole('button', { name: '一覧で確認する' }).click()

    await page
      .getByRole('row')
      .filter({ hasText: number })
      .getByRole('link', { name: '更新する' })
      .click()

    await expect(page.getByRole('heading', { name: '航海スケジュールの更新' })).toBeVisible()
    await expect(page.getByLabel('船名')).toHaveValue('かえで丸')
    await expect(page.getByLabel('運送会社')).toHaveValue('商船三井')

    // 遅延した到着だけを直す
    await page.getByLabel('1 区間目の到着日時').fill(businessLocalDateTime(35, '12:00'))
    await page.getByRole('button', { name: '登録する' }).click()

    // 時刻だけの変更でも差分として見える（見えなければ上書きに進めない）
    await expect(page.getByRole('cell', { name: '日程' })).toBeVisible()
    await page.getByRole('button', { name: 'この内容で上書きする' }).click()
    await expect(page.getByText(`航海 ${number} を更新しました`)).toBeVisible()
  })
})

test.describe('航海スケジュールの検索（US07）', () => {
  test('条件に合う航海が無いときは、条件を緩めて探し直せる', async ({ page }) => {
    await logIn(page, 'routing01')

    // 探す対象を 1 件用意する。読み直さずに画面のリンクで移動する
    await page.goto('/routing/voyages/new')
    await page.getByLabel('航海番号').fill(voyageNumber('C'))
    await page.getByLabel('船名').fill('かえで丸')
    await page.getByLabel('運送会社').fill('商船三井')
    await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
    await page.getByLabel('1 区間目の到着地').selectOption('USLAX')
    await page.getByLabel('1 区間目の出発日時').fill(businessLocalDateTime(30, '09:00'))
    await page.getByLabel('1 区間目の到着日時').fill(businessLocalDateTime(47, '12:00'))
    await page.getByRole('button', { name: '登録する' }).click()
    await page.getByRole('button', { name: '一覧で確認する' }).click()

    // 逆向きの条件。同じ港に寄ることと、その向きに運べることは別である
    await page.getByLabel('出発地').selectOption('USLAX')
    await page.getByLabel('目的地').selectOption('JPTYO')
    await page.getByLabel('出発日（この日以降）').fill(businessLocalDateTime(3650, '00:00').slice(0, 10))
    await page.getByRole('button', { name: '検索する' }).click()

    await expect(page.getByText('条件に合う航海はありませんでした。')).toBeVisible()
    await page.getByRole('button', { name: '条件をすべて外して探し直す' }).click()

    // 条件を外せば、登録した航海が見つかる
    await expect(page.getByText('条件に合う航海はありませんでした。')).toHaveCount(0)
    await expect(page.getByRole('cell', { name: 'かえで丸' })).toBeVisible()
  })
})

test.describe('経路設計への引き渡し（US06）', () => {
  test('営業が引き渡すと、経路設計者が気づいて対象へ行ける', async ({ page }) => {
    await logIn(page, 'sales01')

    // 予約を 1 件作る
    await page.goto('/booking/new')
    await page.getByLabel('荷主').selectOption({ index: 1 })
    await page.getByLabel('重量（kg）').fill('1000')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('到着期限').fill(businessLocalDateTime(60, '00:00').slice(0, 10))
    await page.getByRole('button', { name: '登録する' }).click()
    const registered = await page.getByText(/を発行しました/).textContent()
    // **作った予約を名指しで開く。** `first()` は一覧の並び順に依存し、種データが
    // 1 件増えただけで別の予約を開く（IT9 で輸送中の予約を足したときに実際そうなった）
    const created = /BKG-\d+/.exec(registered ?? '')?.[0] ?? ''
    expect(created, '発行された予約番号が読めない').toMatch(/^BKG-/)

    // 一覧から詳細へ入り、内容を確かめてから引き渡す。
    // 登録後は一覧に居るため、ここでページを読み直さない（読み直すとモックの登録が消える）
    await page.getByRole('link', { name: created }).first().click()
    // 一覧にも同じ言葉が出る（状態の絞り込みと状態列）。詳細の見出しが出てから読む
    await expect(page.getByRole('heading', { name: /^予約 BKG-/ })).toBeVisible()
    await expect(page.getByText('未依頼').last()).toBeVisible()
    // 詳細が描かれてから読む。一覧に居るうちに読むと見出しが「貨物予約」になる
    const bookingId = (await page.getByRole('heading', { level: 1 }).textContent()) ?? ''
    await page.getByRole('button', { name: '経路設計を依頼する' }).click()
    await expect(page.getByText('経路設計を依頼しました')).toBeVisible()

    // ここで切らない。**渡した予約が「経路設計待ち」の範囲に入ったところまで確かめる。**
    // 経路設計者に見える一覧はこの絞り込みそのものであり（サーバが同じ判定で絞る。ADR-015）、
    // ここが繋がっていないと「渡したのに相手に見えない」に気づけない（IT3 の残作業 9）。
    //
    // 利用者を切り替えて確かめないのは、モックが画面の読み直しで消えるためである。
    // **ログインし直す形での往復は実バックエンドの検査で通す**（real-backend.spec.ts）。
    const number = bookingId.replace('予約 ', '').trim()
    await page.getByRole('link', { name: '一覧に戻る' }).click()
    await expect(page.getByRole('link', { name: number })).toBeVisible()
    await page.getByRole('link', { name: number }).click()
    await expect(page.getByRole('heading', { name: `予約 ${number}` })).toBeVisible()
    await expect(page.getByRole('cell', { name: '経路設計を依頼済み' })).toBeVisible()
  })

  /**
   * 通知が作れないぶん、経路設計者はこの導線で気づく。
   *
   * 件数を出すだけでは仕事は進まない。そこから対象の一覧へ行けることまでを 1 本で確かめる。
   */
  test('経路設計者は待っている予約に気づき、そこから対象へ行ける', async ({ page }) => {
    await logIn(page, 'routing01')

    await expect(page.getByText(/経路設計を待っている予約が \d+ 件あります/)).toBeVisible()

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await expect(page.getByRole('heading', { name: '経路設計を待っている予約' })).toBeVisible()

    // 引き渡された予約の中身が見えないと、経路を組む判断ができない
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    // 一覧にも同じ言葉が出る（状態の絞り込みと状態列）。詳細の見出しが出てから読む
    await expect(page.getByRole('heading', { name: /^予約 BKG-/ })).toBeVisible()
    await expect(page.getByText('経路設計を依頼済み').last()).toBeVisible()
    // 引き渡しは営業の操作。経路設計者にはボタンを出さない
    await expect(page.getByRole('button', { name: '経路設計を依頼する' })).toHaveCount(0)
  })
})


/**
 * 経路候補算出（US08 / IT4）。
 *
 * **IT4 は候補一覧の表示まで。** 選択・確定は US09（IT5）であり、ここでは往復が閉じない。
 *
 * 探索の材料（直行 1 本・積み替え 2 本）は動作確認用に置かれている。**直行のほうが
 * 遅く着く**ため、推奨順が「直行を最優先」であることが順序の入れ替わりで分かる。
 */
test.describe('経路候補の算出（US08）', () => {
  test('引き渡された予約から経路候補の一覧まで辿れる', async ({ page }) => {
    await logIn(page, 'routing01')

    // 経路設計者の導線で、引き渡された予約へ入る
    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: /^BKG-/ }).first().click()

    await page.getByRole('link', { name: '経路を割り当て' }).click()
    await expect(page.getByRole('heading', { name: '経路設計' })).toBeVisible()

    // 予約の条件を引き継いだ状態で開く（空のフォームを出さない）
    await expect(page.getByLabel('到着期限')).not.toHaveValue('')

    // 候補が出る。直行便は遅く着くが最優先で並ぶ
    await expect(page.getByText(/候補 \d+ 件（推奨順）/)).toBeVisible()
    await expect(page.getByRole('row').nth(1).getByText('直行')).toBeVisible()

    // 港は名前で示し、UN/LOCODE は併記する
    await expect(page.getByText(/Singapore/).first()).toBeVisible()

    // 費用が概算であることを画面に書く
    await expect(page.getByText(/正式な料金は精算時に確定します/)).toBeVisible()

    // 使えるようになった機能に「次のリリースで」と書いたままにすると、
    // 経路設計者は使える操作を探さなくなる（IT5 で確定が使えるようになった）
    await expect(page.getByText(/次のリリースで使えるようになります/)).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'この経路を選ぶ' }).first()).toBeVisible()
  })

  test('候補の航海から、寄港地と区間ごとの時刻を確かめられる', async ({ page }) => {
    await logIn(page, 'routing01')

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    await page.getByRole('link', { name: '経路を割り当て' }).click()

    // 候補に出た航海が本当に使えるかは、寄港地と区間ごとの時刻を見ないと判断できない。
    // **途中の寄港地と、区間ごとの時刻が実際に出ていることまで見る**
    await page.getByRole('link', { name: 'DEMO-DIRECT' }).first().click()
    await expect(page.getByRole('heading', { name: /航海 DEMO-DIRECT/ })).toBeVisible()
    await expect(page.getByText('寄港と区間（2 区間）')).toBeVisible()
    // 一覧では見えない途中の寄港地
    await expect(page.getByRole('cell', { name: /Yokohama/ }).first()).toBeVisible()
    // 区間ごとの時刻（業務タイムゾーン）
    await expect(page.getByRole('row').nth(1).getByText(/\d{4}-\d{2}-\d{2} \d{2}:\d{2}/).first())
      .toBeVisible()
  })

  test('候補が無いときは、何が効いているかを示して条件を緩められる', async ({ page }) => {
    await logIn(page, 'routing01')

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    await page.getByRole('link', { name: '経路を割り当て' }).click()
    await expect(page.getByText(/候補 \d+ 件（推奨順）/)).toBeVisible()

    // 期限をきつくすると候補が無くなる
    await page.getByLabel('到着期限').fill(businessLocalDateTime(1, '00:00').slice(0, 10))

    await expect(page.getByText(/見つかりませんでした/)).toBeVisible()
    // 「該当なし」で終わらせない。次の操作へ繋ぐ
    await expect(page.getByRole('button', { name: /到着期限を 1 週間延ばす/ })).toBeVisible()
    await expect(page.getByRole('link', { name: /航海スケジュールを見る/ })).toBeVisible()
  })

  test('積み替えを緩めると、その条件で算出し直す', async ({ page }) => {
    await logIn(page, 'routing01')

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    await page.getByRole('link', { name: '経路を割り当て' }).click()

    await page.getByLabel('積み替えの上限').selectOption('0')
    // 直行便だけに絞れば、積み替えの候補は消える
    await expect(page.getByText(/Singapore/)).toHaveCount(0)
  })
})

/**
 * 経路の選択・確定（US09 / US11・IT5）。
 *
 * <p>**予約 → 候補 → 選択 → 確定 → 予約詳細に旅程が出る**までを 1 本で通す。
 * 途中で切ると、確定できたかどうかを画面から確かめないまま緑になる。
 */
test.describe('経路の選択と確定（US09 / US11）', () => {
  test('候補を選んで確定すると、予約詳細に旅程が出る', async ({ page }) => {
    await logIn(page, 'routing01')

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    const bookingLink = page.getByRole('link', { name: /^BKG-/ }).first()
    const bookingId = (await bookingLink.textContent())?.trim() ?? ''
    await bookingLink.click()
    await page.getByRole('link', { name: '経路を割り当て' }).click()
    await expect(page.getByText(/候補 \d+ 件（推奨順）/)).toBeVisible()

    // 押した瞬間には確定しない。取り消す手段の無い操作を一覧の行から直接起こさない
    await page.getByRole('button', { name: 'この経路を選ぶ' }).first().click()
    await expect(page.getByText('この経路で確定しますか')).toBeVisible()
    // 確定すると何が起こるかを先に伝える
    await expect(page.getByText(/予約の状態が「経路提案中」になります/)).toBeVisible()

    await page.getByRole('button', { name: 'この経路で確定する' }).click()

    // 確定できたことは、予約詳細に旅程が出ていることで分かる
    await expect(page).toHaveURL(new RegExp(`/booking/${bookingId}$`))
    await expect(page.getByRole('heading', { name: /割り当て経路（旅程・\d+ 区間）/ }))
      .toBeVisible()
    // 港は名前で、日時は業務タイムゾーン（表示規約）
    await expect(page.getByRole('cell', { name: /Tokyo/ }).first()).toBeVisible()
    await expect(page.getByText(/経路確定/).first()).toBeVisible()
  })

  test('確定を選び直せる（押した時点では予約が動かない）', async ({ page }) => {
    await logIn(page, 'routing01')

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    await page.getByRole('link', { name: '経路を割り当て' }).click()

    await page.getByRole('button', { name: 'この経路を選ぶ' }).first().click()
    await page.getByRole('button', { name: '選び直す' }).click()

    await expect(page.getByText('この経路で確定しますか')).toHaveCount(0)
    // 予約は動いていない。経路設計の入口が残っている
    await expect(page.getByText(/候補 \d+ 件（推奨順）/)).toBeVisible()
  })

  test('経路が組めないときは営業へ戻せる（US10）', async ({ page }) => {
    await logIn(page, 'routing01')

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    await page.getByRole('link', { name: '経路を割り当て' }).click()

    // 期限をきつくすると候補が無くなる
    await page.getByLabel('到着期限').fill(businessLocalDateTime(1, '00:00').slice(0, 10))
    await expect(page.getByText(/見つかりませんでした/)).toBeVisible()

    // 「見つかりませんでした」で終わらせない。荷主との条件交渉へ繋ぐ
    await page.getByRole('button', { name: '条件協議を依頼する' }).click()
    await expect(page.getByText(/この予約は営業へ戻しています/)).toBeVisible()
  })

  test('条件は URL に残り、航海詳細から戻っても入れ直しにならない（US10）', async ({ page }) => {
    await logIn(page, 'routing01')

    await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    await page.getByRole('link', { name: '経路を割り当て' }).click()

    const deadline = businessLocalDateTime(120, '00:00').slice(0, 10)
    await page.getByLabel('到着期限').fill(deadline)
    await expect(page).toHaveURL(new RegExp(`deadline=${deadline}`))

    // 航海を確かめて戻る。条件が消えると、3 件比べる間に同じ条件を 3 回入れ直すことになる
    await page.getByRole('link', { name: 'DEMO-DIRECT' }).first().click()
    await page.getByRole('link', { name: '経路設計に戻る' }).click()
    await expect(page.getByLabel('到着期限')).toHaveValue(deadline)
  })
})

/**
 * 荷主への通知から追跡番号の発行まで（US12・US13・US14・IT6）。
 *
 * <p><strong>「条件が揃わなければスキップ」を書かない</strong>（IT5 の Try 2）。前提が要るなら
 * 前提を作ってから通す。スキップを書くと、前提が崩れた日に「緑だが何も確かめていない」状態に
 * なり、しかもそのことが誰にも見えない。
 *
 * <p>ここでの前提は<strong>種データ</strong>として置く（`mocks/handlers.ts`）。モックは画面の
 * 再読み込みで初期化されるため、1 本の中で利用者を切り替えて辿ることができない。
 * 利用者を切り替える往復は実バックエンド（`real-backend.spec.ts`）が受け持つ。
 */
test.describe('荷主への通知から追跡番号の発行まで（US12 / US13 / US14）', () => {
  /** 経路が決まっていて、まだ通知していない予約（種データ）。 */
  const ROUTED = 'BKG-2026000002'

  /** 荷主の合意を得て確定済みの予約（種データ）。 */
  const CONFIRMED = 'BKG-2026000003'

  test('営業は通知の内容を確かめてから通知し、確定できる', async ({ page }) => {
    await logIn(page, 'sales01')
    await page.goto(`/booking/${ROUTED}`)

    await expect(page.getByText(/営業担当者の手番です。経路が決まりました/)).toBeVisible()

    // 送る前に、何を伝えることになるかを確かめられる（US12-2）
    await expect(page.getByText('経由港')).toBeVisible()
    await expect(page.getByText('到着予定')).toBeVisible()
    // メールが送られないことを画面が言う（US12-3 の代替）
    await expect(page.getByText(/この操作ではメールは送られません/)).toBeVisible()

    await page.getByRole('button', { name: '荷主へ通知する' }).click()
    await expect(page.getByText(/荷主へ通知しました/)).toBeVisible()
    await expect(page.getByText(/荷主の手番です/)).toBeVisible()

    await page.getByRole('button', { name: '予約を確定する' }).click()

    await expect(page.getByText(/経路設計者の手番です/)).toBeVisible()
  })

  /** [ADR-021] 決定 1。「押せるのにできない」を作らない。 */
  test('通知していない予約には、確定のボタンを出さない', async ({ page }) => {
    await logIn(page, 'sales01')
    await page.goto(`/booking/${ROUTED}`)

    await expect(page.getByRole('button', { name: '荷主へ通知する' })).toBeVisible()
    await expect(page.getByRole('button', { name: '予約を確定する' })).toHaveCount(0)
  })

  /** US13-4。戻したことが経路設計者に見えるところまで。 */
  test('荷主が変更を希望したら経路設計へ戻せ、旅程は残る', async ({ page }) => {
    await logIn(page, 'sales01')
    await page.goto(`/booking/${ROUTED}`)
    await page.getByRole('button', { name: '荷主へ通知する' }).click()
    await expect(page.getByText(/荷主の手番です/)).toBeVisible()

    await page.getByRole('button', { name: '経路設計へ戻す' }).click()

    // 経路の状態が作業待ちに戻る。BookingStatus だけ戻しても経路設計者に伝わらない
    await expect(page.getByRole('row', { name: '経路 経路設計を依頼済み' })).toBeVisible()
    // 旅程は残る。どこが合わなかったかを、いまの経路を見ながら相談できる
    await expect(page.getByRole('heading', { name: /割り当て経路（旅程・\d+ 区間）/ }))
      .toBeVisible()
  })

  test('経路設計者は発行待ちの一覧から追跡番号を発行できる', async ({ page }) => {
    await logIn(page, 'routing01')

    // 件数だけ出しても仕事は進まない。ここから対象へ行ける（US13-3）
    await page.getByRole('link', { name: '追跡番号の発行を待っている予約を見る' }).click()
    await expect(page.getByRole('heading', { name: '追跡番号の発行を待っている予約' }))
      .toBeVisible()
    await page.getByRole('link', { name: CONFIRMED }).click()

    await page.getByRole('button', { name: '追跡番号を発行する' }).click()

    // 形式そのものが契約になる（ADR-011 と同じ形）
    await expect(page.getByText(/^TRK-\d{8}-\d{4}$/)).toBeVisible()
    // 荷主には届かない。届いたと思われると、問い合わせに営業が答えられなくなる
    await expect(page.getByText(/荷主には自動で送られていません/)).toBeVisible()
  })

  test('確定していない予約には、発行のボタンを出さない', async ({ page }) => {
    await logIn(page, 'routing01')
    await page.goto(`/booking/${ROUTED}`)

    await expect(page.getByRole('heading', { name: '経路設計' })).toBeVisible()
    await expect(page.getByRole('button', { name: '追跡番号を発行する' })).toHaveCount(0)
  })
})

/**
 * ロックされたアカウントの解除（US32・IT6）。
 */
test.describe('ロックされたアカウントの解除（US32）', () => {
  test('管理者はロック中の一覧から解除できる', async ({ page }) => {
    await logIn(page, 'admin01')

    await page.getByRole('link', { name: 'ロックされたアカウントを解除する' }).click()
    await expect(page.getByRole('heading', { name: 'アカウント管理' })).toBeVisible()

    // 解除の判断に要らないものは出さない
    await expect(page.getByText('sales02')).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '失敗回数' })).toBeVisible()

    await page.getByRole('button', { name: '解除する' }).first().click()

    await expect(page.getByText(/いまロックされているアカウントはありません/)).toBeVisible()
  })

  /** US32-4。押した先で 403 になる画面へ誘導しない。 */
  test('管理者以外にはアカウント管理のメニューを出さない', async ({ page }) => {
    await logIn(page, 'sales01')

    await expect(page.getByRole('link', { name: 'アカウント管理' })).toHaveCount(0)
  })
})
