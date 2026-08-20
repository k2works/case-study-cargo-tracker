import { expect, test } from '@playwright/test'
import { businessLocalDateTime } from './support/business-time.js'

/**
 * IT3 の受け入れ。US24（航海登録）・US25（航海更新）・US07（航海検索）・US06（引き渡し）。
 *
 * IT3 のスコープ外（依存先が未実装、または設計にモデルが無い）:
 * - 港湾制約による絞り込み（US07。US08 で必要性ごと判断する）
 * - 経路設計依頼のメール通知（US06。画面上の気づく手段で代替）
 * - 予約の訂正（US06。次イテレーション以降）
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
    await expect(page.getByText(/を発行しました/)).toBeVisible()

    // 一覧から詳細へ入り、内容を確かめてから引き渡す。
    // 登録後は一覧に居るため、ここでページを読み直さない（読み直すとモックの登録が消える）
    await page.getByRole('link', { name: /^BKG-/ }).first().click()
    await expect(page.getByText('未依頼')).toBeVisible()
    await page.getByRole('button', { name: '経路設計を依頼する' }).click()
    await expect(page.getByText('経路設計を依頼しました')).toBeVisible()

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
    await expect(page.getByText('経路設計を依頼済み')).toBeVisible()
    // 引き渡しは営業の操作。経路設計者にはボタンを出さない
    await expect(page.getByRole('button', { name: '経路設計を依頼する' })).toHaveCount(0)
  })
})
