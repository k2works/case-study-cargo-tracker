import { expect, test } from '@playwright/test'

/**
 * IT2 の受け入れ。US03（法人荷主）・US04（貨物予約）・US05（危険物・冷凍）に対応する。
 *
 * 実装より先に置く（Red）。ここが緑になるまで、これらのストーリーは「動く」と言わない。
 *
 * IT2 のスコープ外（依存先が未実装）:
 * - 経路設計者への通知（US06・IT3）
 * - 見積との整合確認（US01・IT12）
 * - 特別情報に応じた航海・ルートの絞り込み（US08/US09・IT4/IT5）
 */

async function logInAsSales(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill('sales01')
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}


test.describe('法人荷主の登録（US03）', () => {
  test('法人を選ぶと契約情報が現れ、登録すると荷主コードが発行される', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/shippers/new')

    // 個人のあいだは契約情報を尋ねない。個人に契約番号を持たせないため
    await expect(page.getByLabel('契約番号')).toHaveCount(0)

    await page.getByLabel('荷主種別').selectOption('CORPORATE')
    await expect(page.getByLabel('契約番号')).toBeVisible()
    await expect(page.getByLabel('割引率（%）')).toBeVisible()

    await page.getByLabel('氏名/社名').fill('丸紅商事株式会社')
    await page.getByLabel('メールアドレス').fill('corp@example.com')
    await page.getByLabel('住所').fill('東京都千代田区 1-1-1')
    await page.getByLabel('契約番号').fill('CN-2026-0001')
    await page.getByLabel('割引率（%）').fill('12.5')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByText(/SHP-\d{6}/)).toBeVisible()
  })

  test('割引率が 30% を超えると登録できない', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/shippers/new')

    await page.getByLabel('荷主種別').selectOption('CORPORATE')
    await page.getByLabel('氏名/社名').fill('過大割引商事')
    await page.getByLabel('メールアドレス').fill('toomuch@example.com')
    await page.getByLabel('住所').fill('東京都港区 2-2-2')
    await page.getByLabel('契約番号').fill('CN-2026-0002')
    await page.getByLabel('割引率（%）').fill('30.1')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByText(/割引率は 0〜30/)).toBeVisible()
  })

  test('法人を選んだのに契約番号が空だと登録できない', async ({ page }) => {
    // 契約番号の無い法人が溜まると、US22（法人割引）で全件の追加入力が発生する
    await logInAsSales(page)
    await page.goto('/booking/shippers/new')

    await page.getByLabel('荷主種別').selectOption('CORPORATE')
    await page.getByLabel('氏名/社名').fill('契約番号なし商事')
    await page.getByLabel('メールアドレス').fill('nocontract@example.com')
    await page.getByLabel('住所').fill('東京都新宿区 3-3-3')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByRole('alert')).toHaveText('法人荷主には契約番号が必要です')
    await expect(page).toHaveURL(/\/booking\/shippers\/new/)
  })

  test('法人から個人に戻すと、入力した契約情報は残らない', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/shippers/new')

    await page.getByLabel('荷主種別').selectOption('CORPORATE')
    await page.getByLabel('契約番号').fill('CN-9999-9999')
    await page.getByLabel('荷主種別').selectOption('INDIVIDUAL')
    await expect(page.getByLabel('契約番号')).toHaveCount(0)

    await page.getByLabel('荷主種別').selectOption('CORPORATE')
    await expect(page.getByLabel('契約番号')).toHaveValue('')
  })
})

test.describe('貨物予約の登録（US04）', () => {
  test('荷主を選び、貨物仕様と輸送条件を入力すると予約番号が発行される', async ({ page }) => {
    await logInAsSales(page)

    // ダッシュボードから辿れなければ、営業担当者はこの機能に出会わない
    await page.goto('/dashboard')
    await page.getByRole('link', { name: '貨物予約を見る' }).click()
    await expect(page).toHaveURL(/\/booking$/)

    await page.getByRole('link', { name: '新規登録' }).click()
    await expect(page).toHaveURL(/\/booking\/new/)

    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('貨物種別').selectOption('GENERAL')
    await page.getByLabel('重量（kg）').fill('12000')
    await page.getByLabel('個数').fill('20')
    await page.getByLabel('品名').fill('電子部品')
    await page.getByLabel('長さ（cm）').fill('120')
    await page.getByLabel('幅（cm）').fill('80')
    await page.getByLabel('高さ（cm）').fill('100')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('希望出発日').fill('2027-09-01')
    await page.getByLabel('到着期限').fill('2027-09-20')
    await page.getByRole('button', { name: '登録する' }).click()

    // 登録完了は一覧に戻す。予約詳細は IT3 以降
    await expect(page).toHaveURL(/\/booking(\?|$)/)
    // 採番された番号は登録の知らせと一覧の両方に出る
    await expect(page.getByRole('status')).toHaveText(/BKG-\d{10}/)
    await expect(page.getByRole('cell', { name: '仮受付' })).toBeVisible()
  })

  test('到着期限に過去の日付は入れられない', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/new')

    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('貨物種別').selectOption('GENERAL')
    await page.getByLabel('重量（kg）').fill('1000')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('到着期限').fill('2020-01-01')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByRole('alert')).toHaveText(/到着期限に過去の日付は指定できません/)
    await expect(page).toHaveURL(/\/booking\/new/)
  })

  test('出発地と目的地が同じ予約は登録できない', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/new')

    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('貨物種別').selectOption('GENERAL')
    await page.getByLabel('重量（kg）').fill('1000')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('JPTYO')
    await page.getByLabel('到着期限').fill('2027-09-20')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByRole('alert')).toHaveText(/出発地と目的地は同じにできません/)
    await expect(page).toHaveURL(/\/booking\/new/)
  })

  test('荷主ロールは予約画面に入れない', async ({ page }) => {
    // 利用者と荷主を結ぶキーが無く、自分の予約だけに絞り込めない（ADR-008）
    await page.goto('/login')
    await page.getByLabel('利用者 ID').fill('shipper01')
    await page.getByLabel('パスワード').fill('password')
    await page.getByRole('button', { name: 'ログイン' }).click()
    // ログインの完了を待たずに次へ進むと、まだ認証されておらず
    // ログイン画面に戻されただけの状態を「403 だ」と取り違える
    await expect(page).toHaveURL(/\/dashboard/)

    await page.goto('/booking')
    await expect(page).toHaveURL(/\/403/)
    await expect(page.getByRole('heading', { name: /権限がありません/ })).toBeVisible()
  })
})

test.describe('危険物・冷凍貨物の予約（US05）', () => {
  test('危険物を選ぶと申告欄が現れ、未入力では登録できない', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/new')

    await expect(page.getByLabel('UN 番号')).toHaveCount(0)

    await page.getByLabel('貨物種別').selectOption('HAZARDOUS')
    await expect(page.getByLabel('危険物クラス')).toBeVisible()
    await expect(page.getByLabel('UN 番号')).toBeVisible()
    await expect(page.getByLabel('正式品名')).toBeVisible()

    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('重量（kg）').fill('500')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('到着期限').fill('2027-09-20')
    await page.getByRole('button', { name: '登録する' }).click()

    // URL だけでは判別しない。登録に失敗しても成功しても、遷移しなければ同じ URL に見える。
    // 「なぜ登録できなかったか」が読める形で出ていることまで確かめる
    await expect(page.getByRole('alert')).toContainText(/危険物|必須/)
    await expect(page).toHaveURL(/\/booking\/new/)
  })

  test('冷凍貨物は温度条件が必須で、下限が上限を超えると登録できない', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/new')

    await page.getByLabel('貨物種別').selectOption('REFRIGERATED')
    await expect(page.getByLabel('保管温度の下限（℃）')).toBeVisible()
    await expect(page.getByLabel('保管温度の上限（℃）')).toBeVisible()

    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('重量（kg）').fill('800')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('到着期限').fill('2027-09-20')
    await page.getByLabel('保管温度の下限（℃）').fill('-10')
    await page.getByLabel('保管温度の上限（℃）').fill('-20')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page.getByRole('alert')).toHaveText(/下限が上限を超えています/)
    await expect(page).toHaveURL(/\/booking\/new/)
  })

  test('種別を一般貨物に戻すと、危険物の入力は残らない', async ({ page }) => {
    await logInAsSales(page)
    await page.goto('/booking/new')

    await page.getByLabel('貨物種別').selectOption('HAZARDOUS')
    await page.getByLabel('UN 番号').fill('UN1263')
    await page.getByLabel('貨物種別').selectOption('GENERAL')
    await expect(page.getByLabel('UN 番号')).toHaveCount(0)

    await page.getByLabel('貨物種別').selectOption('HAZARDOUS')
    await expect(page.getByLabel('UN 番号')).toHaveValue('')
  })

  test('危険物の予約は一覧で種別が分かる', async ({ page }) => {
    // 経路設計（IT3）が読む前に、まず人が見て取り違えないこと
    await logInAsSales(page)
    await page.goto('/booking/new')

    await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
    await page.getByLabel('貨物種別').selectOption('HAZARDOUS')
    await page.getByLabel('重量（kg）').fill('500')
    await page.getByLabel('出発地').selectOption('JPTYO')
    await page.getByLabel('目的地').selectOption('USLAX')
    await page.getByLabel('到着期限').fill('2027-09-20')
    await page.getByLabel('危険物クラス').fill('Class 3')
    await page.getByLabel('UN 番号').fill('UN1263')
    await page.getByLabel('正式品名').fill('PAINT')
    await page.getByRole('button', { name: '登録する' }).click()

    await expect(page).toHaveURL(/\/booking(\?|$)/)
    await expect(page.getByRole('cell', { name: '危険物' })).toBeVisible()
  })
})
