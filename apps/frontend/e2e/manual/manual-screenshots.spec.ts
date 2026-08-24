import { expect, test, type Page } from '@playwright/test'

/**
 * docs/manual/assets/ の画面キャプチャを生成する。
 *
 * 手作業で PNG を置くと、次回の再生成で上書きされて撮り漏れに気づけない。画面を変えたら
 * このファイルにも手を入れ、同じイテレーションの中でキャプチャと本文を更新する。
 */
const ASSETS = '../../docs/manual/assets'

async function login(page: Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

async function registerShipper(page: Page, name: string, email: string) {
  await page.goto('/booking/shippers/new')
  await page.getByLabel('氏名/社名').fill(name)
  await page.getByLabel('メールアドレス').fill(email)
  await page.getByLabel('住所').fill('東京都千代田区丸の内 1-1-1')
  await page.getByLabel('連絡先（任意）').fill('03-1234-5678')
  await page.getByRole('button', { name: '登録する' }).click()
}

test('02-login（ログイン画面）', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/02-login.png`, fullPage: true })
})

test('02-dashboard（ダッシュボード）', async ({ page }) => {
  await login(page, 'sales01')
  await expect(page.getByRole('heading', { name: '営業ダッシュボード' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/02-dashboard.png`, fullPage: true })
})

test('03-shipper-register（荷主登録）', async ({ page }) => {
  await login(page, 'sales01')
  await page.goto('/booking/shippers/new')
  await expect(page.getByRole('heading', { name: '荷主登録' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-register.png`, fullPage: true })
})

test('03-shipper-list（荷主一覧）', async ({ page }) => {
  await login(page, 'sales01')
  // 空の一覧では読者が項目の意味をつかめないため、代表的な 1 件を登録してから撮る
  await registerShipper(page, '丸の内商事株式会社', 'marunouchi@example.com')

  // 画面内の導線で移動する。goto で読み直すとモックの登録内容が消える
  await page.getByRole('link', { name: '荷主一覧へ戻る' }).click()
  await expect(page.getByRole('cell', { name: '丸の内商事株式会社' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-list.png`, fullPage: true })
})

test('03-shipper-duplicate（重複の確認）', async ({ page }) => {
  await login(page, 'sales01')
  await registerShipper(page, '大手町物流株式会社', 'otemachi@example.com')

  await page.getByRole('button', { name: '続けて登録する' }).click()
  await page.getByLabel('氏名/社名').fill('大手町物流株式会社 横浜支店')
  await page.getByLabel('メールアドレス').fill('otemachi@example.com')
  await page.getByLabel('住所').fill('神奈川県横浜市中区 2-2-2')
  await page.getByRole('button', { name: '登録する' }).click()

  await expect(page.getByText(/既に登録されています/)).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-duplicate.png`, fullPage: true })
})

test('03-shipper-corporate（法人契約情報の入力）', async ({ page }) => {
  await login(page, 'sales01')
  await page.goto('/booking/shippers/new')
  await page.getByLabel('荷主種別').selectOption('CORPORATE')
  await page.getByLabel('氏名/社名').fill('丸紅商事株式会社')
  await page.getByLabel('メールアドレス').fill('corp-manual@example.com')
  await page.getByLabel('住所').fill('東京都千代田区丸の内 1-1-1')
  await page.getByLabel('契約番号').fill('CN-2026-0001')
  await page.getByLabel('割引率（%）').fill('12.5')
  await expect(page.getByLabel('契約番号')).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-corporate.png`, fullPage: true })
})

test('04-booking-register（貨物予約の登録）', async ({ page }) => {
  await login(page, 'sales01')
  await page.goto('/booking/new')
  await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
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

  await page.screenshot({ path: `${ASSETS}/04-booking-register.png`, fullPage: true })
})

test('04-booking-list（貨物予約の一覧）', async ({ page }) => {
  await login(page, 'sales01')

  // 一覧に何も無い状態を撮ると、読者は列の意味を確かめられない
  await page.goto('/booking/new')
  await page.getByLabel('荷主', { exact: true }).selectOption({ index: 1 })
  await page.getByLabel('貨物種別').selectOption('HAZARDOUS')
  await page.getByLabel('重量（kg）').fill('500')
  await page.getByLabel('出発地').selectOption('JPTYO')
  await page.getByLabel('目的地').selectOption('USLAX')
  await page.getByLabel('到着期限').fill('2027-09-20')
  await page.getByLabel('危険物クラス').selectOption('3')
  await page.getByLabel('UN 番号').fill('UN1263')
  await page.getByLabel('正式品名').fill('PAINT')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByRole('status')).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/04-booking-list.png`, fullPage: true })
})

test('04-booking-detail（予約の詳細と経路設計への引き渡し）', async ({ page }) => {
  await login(page, 'sales01')
  await page.goto('/booking')

  // 一覧から詳細へ入る導線そのものが本文の説明対象になる
  await page.getByRole('link', { name: /^BKG-/ }).first().click()
  await expect(page.getByRole('heading', { name: /^予約 BKG-/ })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/04-booking-detail.png`, fullPage: true })
})

test('05-voyage-list（航海スケジュールの一覧・検索）', async ({ page }) => {
  await login(page, 'routing01')

  // 一覧に何も無い状態を撮ると、読者は列の意味を確かめられない
  await page.goto('/routing/voyages/new')
  await page.getByLabel('航海番号').fill('V0100')
  await page.getByLabel('船名').fill('さくら丸')
  await page.getByLabel('運送会社').fill('日本郵船')
  await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
  await page.getByLabel('1 区間目の到着地').selectOption('USLAX')
  await page.getByLabel('1 区間目の出発日時').fill('2027-10-01T09:00')
  await page.getByLabel('1 区間目の到着日時').fill('2027-10-18T12:00')
  await page.getByRole('button', { name: '登録する' }).click()
  await page.getByRole('button', { name: '一覧で確認する' }).click()
  await expect(page.getByRole('cell', { name: 'V0100' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/05-voyage-list.png`, fullPage: true })
})

test('05-voyage-register（航海スケジュールの登録）', async ({ page }) => {
  await login(page, 'routing01')
  await page.goto('/routing/voyages/new')
  await page.getByLabel('航海番号').fill('V0200')
  await page.getByLabel('船名').fill('つばき丸')
  await page.getByLabel('運送会社').fill('商船三井')
  await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
  await page.getByLabel('1 区間目の到着地').selectOption('SGSIN')
  await page.getByLabel('1 区間目の出発日時').fill('2027-11-01T09:00')
  await page.getByLabel('1 区間目の到着日時').fill('2027-11-08T12:00')

  await page.screenshot({ path: `${ASSETS}/05-voyage-register.png`, fullPage: true })
})

test('05-voyage-difference（更新時の差分確認）', async ({ page }) => {
  await login(page, 'routing01')
  await page.goto('/routing/voyages/new')

  // 同じ航海番号を 2 回。ページを読み直すとモックの登録が消えるため、同じ画面で続ける
  // 2 回目は船名と到着日時を変える。マニュアル本文が「日程」の差分を説明しているため、
  // 実務でいちばん多い遅延（到着だけがずれる）が画像にも出るようにする
  async function submit(vesselName: string, arrival: string) {
    await page.getByLabel('航海番号').fill('V0300')
    await page.getByLabel('船名').fill(vesselName)
    await page.getByLabel('運送会社').fill('日本郵船')
    await page.getByLabel('1 区間目の出発地').selectOption('JPTYO')
    await page.getByLabel('1 区間目の到着地').selectOption('USLAX')
    await page.getByLabel('1 区間目の出発日時').fill('2027-12-01T09:00')
    await page.getByLabel('1 区間目の到着日時').fill(arrival)
    await page.getByRole('button', { name: '登録する' }).click()
  }

  await submit('さくら丸', '2027-12-18T12:00')
  await expect(page.getByText('航海 V0300 を登録しました')).toBeVisible()
  await submit('つばき丸', '2027-12-20T12:00')
  await expect(page.getByRole('button', { name: 'この内容で上書きする' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/05-voyage-difference.png`, fullPage: true })
})

test('05-voyage-detail（航海スケジュールの詳細）', async ({ page }) => {
  await login(page, 'routing01')

  // 動作確認用の航海には途中の寄港がある。1 区間だけの航海を撮ると、
  // この画面が何のためにあるのか（途中の寄港と時刻を見る）が伝わらない
  await page.goto('/routing/voyages')
  await page.getByRole('link', { name: 'DEMO-DIRECT' }).click()
  await expect(page.getByText(/寄港と区間/)).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/05-voyage-detail.png`, fullPage: true })
})

test('06-route-candidates（経路候補の一覧）', async ({ page }) => {
  await login(page, 'routing01')

  await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
  await page.getByRole('link', { name: /^BKG-/ }).first().click()
  await page.getByRole('link', { name: '経路を割り当て' }).click()
  await expect(page.getByText(/候補 \d+ 件（推奨順）/)).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/06-route-candidates.png`, fullPage: true })
})

test('06-route-empty（候補が見つからないとき）', async ({ page }) => {
  await login(page, 'routing01')

  await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
  await page.getByRole('link', { name: /^BKG-/ }).first().click()
  await page.getByRole('link', { name: '経路を割り当て' }).click()
  await expect(page.getByText(/候補 \d+ 件（推奨順）/)).toBeVisible()

  // 期限を今日にすると、どの便も間に合わない
  const today = new Date().toISOString().slice(0, 10)
  await page.getByLabel('到着期限').fill(today)
  await expect(page.getByText(/見つかりませんでした/)).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/06-route-empty.png`, fullPage: true })
})

test('03-shipper-edit（荷主の編集）', async ({ page }) => {
  await login(page, 'sales01')

  await page.goto('/booking/shippers')
  await page.getByRole('link', { name: '編集' }).first().click()
  await expect(page.getByRole('heading', { name: '荷主の編集' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/03-shipper-edit.png`, fullPage: true })
})

test('06-route-confirm（経路の確定を確かめる）', async ({ page }) => {
  await login(page, 'routing01')

  await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
  await page.getByRole('link', { name: /^BKG-/ }).first().click()
  await page.getByRole('link', { name: '経路を割り当て' }).click()
  await page.getByRole('button', { name: 'この経路を選ぶ' }).first().click()
  await expect(page.getByText('この経路で確定しますか')).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/06-route-confirm.png`, fullPage: true })
})

test('04-booking-itinerary（予約詳細の割り当て経路）', async ({ page }) => {
  await login(page, 'routing01')

  await page.getByRole('link', { name: '経路設計を待っている予約を見る' }).click()
  await page.getByRole('link', { name: /^BKG-/ }).first().click()
  await page.getByRole('link', { name: '経路を割り当て' }).click()
  await page.getByRole('button', { name: 'この経路を選ぶ' }).first().click()
  await page.getByRole('button', { name: 'この経路で確定する' }).click()
  await expect(page.getByRole('heading', { name: /割り当て経路（旅程・\d+ 区間）/ })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/04-booking-itinerary.png`, fullPage: true })
})

test('04-booking-notify（荷主への通知と確定）', async ({ page }) => {
  await login(page, 'sales01')

  // 経路が決まった予約は種データで用意している（前提を作ってから撮る）
  await page.goto('/booking/BKG-2026000002')
  await expect(page.getByText(/営業担当者の手番です。経路が決まりました/)).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/04-booking-notify.png`, fullPage: true })
})

test('04-tracking-number（発行した追跡番号の確認）', async ({ page }) => {
  await login(page, 'routing01')

  await page.getByRole('link', { name: '追跡番号の発行を待っている予約を見る' }).click()
  await page.getByRole('link', { name: 'BKG-2026000003' }).click()
  await page.getByRole('button', { name: '追跡番号を発行する' }).click()
  await expect(page.getByText(/^TRK-\d{8}-\d{4}$/)).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/04-tracking-number.png`, fullPage: true })
})

test('06-issue-tracking（追跡番号の発行）', async ({ page }) => {
  await login(page, 'routing01')

  await page.getByRole('link', { name: '追跡番号の発行を待っている予約を見る' }).click()
  await expect(page.getByRole('heading', { name: '追跡番号の発行を待っている予約' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/06-issue-tracking.png`, fullPage: true })
})

test('07-locked-accounts（ロックされたアカウントの解除）', async ({ page }) => {
  await login(page, 'admin01')

  await page.getByRole('link', { name: 'ロックされたアカウントを解除する' }).click()
  await expect(page.getByRole('heading', { name: 'アカウント管理' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/07-locked-accounts.png`, fullPage: true })
})

test('08-handling（荷役作業の記録）', async ({ page }) => {
  await login(page, 'handler01')

  await page.getByRole('link', { name: '荷役作業を記録する' }).click()
  await expect(page.getByRole('heading', { name: '荷役作業の記録' })).toBeVisible()

  // 種別ごとに出る欄（航海番号・荷受人の確認）は、選ばないと現れない。
  // 空のフォームだけを撮ると、マニュアルの項目表と画面が食い違って見える
  await page.getByLabel('追跡番号').fill('TRK-20260823-0001')
  await page.getByLabel('作業の種別').selectOption('LOAD')
  await page.getByLabel('作業場所').selectOption('JPTYO')

  await page.screenshot({ path: `${ASSETS}/08-handling.png`, fullPage: true })
})

test('08-handling-history（この貨物の作業履歴）', async ({ page }) => {
  await login(page, 'handler01')
  await page.goto('/handling')

  // 履歴は記録しないと出ない。マニュアル 8.4 の表と画面を合わせるため、
  // 1 件記録してから撮る
  await page.getByLabel('追跡番号').fill('TRK-20260823-0001')
  await page.getByLabel('作業の種別').selectOption('RECEIVE')
  await page.getByLabel('作業場所').selectOption('JPTYO')
  await page.getByLabel('作業日時').fill('2027-09-02T09:00')
  await page.getByRole('button', { name: '記録する' }).click()
  await expect(page.getByRole('table')).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/08-handling-history.png`, fullPage: true })
})

test('09-tracking-lookup（追跡照会・ログイン不要）', async ({ page }) => {
  // **ログインしない。**この画面は認証の外にある（US18-5）。ログインしてから撮ると、
  // 荷主が見る画面とは違うもの（ナビ付き）が載ってしまう
  await page.goto('/tracking/TRK-20260823-0001')
  await expect(page.getByRole('heading', { name: '貨物の追跡' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/09-tracking-lookup.png`, fullPage: true })
})

test('09-tracking-manage（貨物状態の管理）', async ({ page }) => {
  await login(page, 'tracker01')
  await page.goto('/tracking/manage')

  // 空の入力欄だけを撮ると、マニュアル 9.2 の手順表と画面が食い違って見える。
  // 貨物を表示した状態にしてから撮る
  await page.getByLabel('追跡番号').fill('TRK-20260823-0001')
  await page.getByRole('button', { name: '貨物を表示する' }).click()
  await expect(page.getByRole('heading', { name: 'TRK-20260823-0001' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/09-tracking-manage.png`, fullPage: true })
})

test('09-open-exceptions（未解決の例外一覧）', async ({ page }) => {
  await login(page, 'tracker01')
  await page.goto('/tracking/manage')

  // 例外が 1 件も無いと「未解決の例外はありません」だけの画面になり、
  // マニュアル 9.4 の説明（並び順・列）と対応しない。起票してから撮る
  await page.getByLabel('追跡番号').fill('TRK-20260823-0001')
  await page.getByRole('button', { name: '貨物を表示する' }).click()
  await page.getByRole('button', { name: '例外を起票する' }).click()
  await page.getByLabel('例外の種別').selectOption('DELAY')
  await page.getByLabel('発生状況').fill('台風により出港が遅れています')
  await page.getByRole('button', { name: '起票する' }).click()
  await expect(page.getByText('起票しました。')).toBeVisible()

  await page.goto('/tracking/manage/exceptions')
  await expect(page.getByRole('heading', { name: '未解決の例外' })).toBeVisible()

  await page.screenshot({ path: `${ASSETS}/09-open-exceptions.png`, fullPage: true })
})
