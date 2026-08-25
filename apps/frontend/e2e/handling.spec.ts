import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

/**
 * IT7 の受け入れ。US15（荷役作業の記録）・US16（引取作業の記録）に対応する。
 *
 * **前提は種データで用意する。** 「予約 → 引き渡し → 経路 → 通知 → 確定 → 発行」を毎回
 * 通すと、荷役と関係のない場所で落ちて原因が読めない。かといって「条件が揃わなければ
 * スキップ」にすると、**通っていないことに気づけない**。
 *
 * IT7 のスコープ外:
 * - 荷主への状態変更通知（US15-5 は代替。通知基盤は US19・IT8）
 * - 精算の開始（US16-4 は範囲外。`CargoDeliveredEvent` は IT12）
 *
 * **IT9 で通関ガードが入った**（US29-3）。引取は<strong>通関済でなければ通らない</strong>
 * ため、引取を確かめるテストは先に通関を済ませる。済ませずに書くと、
 * <strong>荷受人の確認の守りを一度も踏まない</strong>——外しても緑のままになる。
 */

/** 種データの追跡番号（`src/mocks/data.ts` の BKG-2026000004）。 */
const TRACKING_NUMBER = 'TRK-20260823-0001'

async function logInAs(page: Page, userId: string) {
  await page.goto('/login')
  await page.getByLabel('利用者 ID').fill(userId)
  await page.getByLabel('パスワード').fill('password')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

async function logInAsHandler(page: Page) {
  await logInAs(page, 'handler01')
}

async function record(
  page: Page,
  options: { type: string; location: string; voyageNumber?: string; confirmation?: string },
) {
  await page.getByLabel('追跡番号').fill(TRACKING_NUMBER)
  await page.getByLabel('作業の種別').selectOption(options.type)
  await page.getByLabel('作業場所').selectOption(options.location)
  // 業務タイムゾーンで入力する。端末の設定に依存させない
  await page.getByLabel('作業日時').fill('2027-09-02T09:00')
  if (options.voyageNumber !== undefined) {
    await page.getByLabel('航海番号').fill(options.voyageNumber)
  }
  if (options.confirmation !== undefined) {
    await page.getByLabel('荷受人の確認').fill(options.confirmation)
  }
  await page.getByRole('button', { name: '記録する' }).click()
  // 引取だけは確認を挟む（IT8 返済枠 0.9）。ここでは押し切って、結果を見る
  const confirm = page.getByRole('button', { name: 'この貨物の引取を記録する' })
  if ((await confirm.count()) > 0) {
    await confirm.click()
  }
}

test.describe('荷役作業の記録（US15・US16）', () => {
  test.beforeEach(async ({ page }) => {
    await logInAsHandler(page)
    // 荷役作業員は、ダッシュボードから自分の仕事へ行ける（ロール別の到達性）
    await page.getByRole('link', { name: '荷役作業を記録する' }).click()
    await expect(page.getByRole('heading', { name: '荷役作業の記録' })).toBeVisible()
  })

  test('追跡番号から受領・積込を記録すると、履歴に時系列で残る', async ({ page }) => {
    await record(page, { type: 'RECEIVE', location: 'JPTYO' })
    await expect(page.getByText('記録しました。')).toBeVisible()

    await record(page, { type: 'LOAD', location: 'JPTYO', voyageNumber: 'V-SEED-3' })

    const history = page.getByRole('table')
    await expect(history.getByText('受領')).toBeVisible()
    await expect(history.getByText('積込')).toBeVisible()
  })

  /** US15-6。番号を読み違えるのが最も多い。何を直せばよいかを伝える。 */
  test('存在しない追跡番号は、何を直せばよいかを伝える', async ({ page }) => {
    await page.getByLabel('追跡番号').fill('TRK-99999999-9999')
    await page.getByLabel('作業の種別').selectOption('RECEIVE')
    await page.getByLabel('作業場所').selectOption('JPTYO')
    await page.getByLabel('作業日時').fill('2027-09-02T09:00')
    await page.getByRole('button', { name: '記録する' }).click()

    await expect(page.getByText(/番号を確かめてください/)).toBeVisible()
  })

  /**
   * US15-7・[ADR-023] 決定 3。
   *
   * **警告は出すが記録は拒まない。** 現場ではすでに作業が終わっており、拒むと実際に
   * 起きたことがどこにも残らない。
   */
  test('予定ルート外の作業は、警告が出たうえで記録に残る', async ({ page }) => {
    await record(page, { type: 'UNLOAD', location: 'SGSIN', voyageNumber: 'V-SEED-3' })

    await expect(page.getByText(/予定と違う場所での作業です/)).toBeVisible()
    await expect(page.getByRole('table').getByText('予定外')).toBeVisible()
  })

  /** US16-2・成功基準 3。通関ガードが無い IT7 では、これが唯一の歯止めである。 */
  test('荷受人の確認がない引取は記録できない', async ({ page }) => {
    await record(page, { type: 'CLAIM', location: 'USLAX' })

    await expect(page.getByText('荷受人の確認は必須です')).toBeVisible()
    await expect(page.getByRole('table')).toBeHidden()
  })

  /**
   * US16-1〜US16-3 の成功パスは<strong>実環境のテストが見る</strong>
   * （`real-backend.spec.ts` の「通関済なら、荷受人の確認を入れた引取が記録される」）。
   *
   * <p>ここで書けないのは、通関済にできるのが追跡管理者だけであり、
   * <strong>ロールを切り替えるとページが読み直されてモックの状態が消える</strong>ため
   * である。**「状態が作れないから前提を省く」を選ぶと、通関ガードの手前で止まる引取を
   * 「記録できた」と読み違える**——実際、IT9 でこのテストはそう壊れた。
   */

  /**
   * **代替であることを画面に書く**（[ADR-023] 決定 4・US15-5）。
   *
   * 書かないと、作業員は「記録すれば荷主に伝わる」と受け取る。
   *
   * <p><strong>通関の但し書きは IT9 で外した。</strong>ガードが入った以上その文は誤りで
   * あり、残すと「仕組みは見ていない」と読んだ作業員が現物の書類を確かめずに引き渡す。
   * ここでは<strong>消えていること</strong>を固定する——消し忘れを踏まないために。
   */
  test('通知が代替であることは書いてあり、通関の但し書きは残っていない', async ({
    page,
  }) => {
    await expect(page.getByText(/荷主へは自動で通知されません/)).toBeVisible()

    await page.getByLabel('作業の種別').selectOption('CLAIM')

    await expect(page.getByText(/通関の確認は、まだ仕組みでは行われません/)).toBeHidden()
  })

  /** US29-3。**申告が無い貨物ほど漏れる**——名簿方式は未登録を素通りさせない。 */
  test('通関申告が無い貨物の引取は、荷受人の確認を入れても断られる', async ({ page }) => {
    await record(page, {
      type: 'CLAIM',
      location: 'USLAX',
      confirmation: '山田太郎（受取担当）',
    })

    await expect(page.getByText(/通関申告がありません/)).toBeVisible()
    await expect(page.getByRole('table')).toBeHidden()
  })
})
