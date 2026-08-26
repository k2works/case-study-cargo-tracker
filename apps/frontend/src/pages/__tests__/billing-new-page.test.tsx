import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { billingHandlers, invoices } from '../../mocks/handlers/billing'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { BillingNewPage } from '../billing-new-page'

/**
 * 料金算出（US21・US22）。
 *
 * <p>ここで確かめるのは、**E2E では判別しにくいこと**である。
 * <ul>
 *   <li>出ない側——個人荷主に割引の欄が「無い」・根拠が無いときに枠が「出ない」
 *   <li>入力の拒み方——根拠の無い調整を作らせない
 * </ul>
 *
 * <p>E2E は「見えるべきものが見える」ことを確かめるのが得意で、
 * <strong>見えてはいけないものが見えていないこと</strong>は落としやすい。
 */
function renderAt(bookingId: string) {
  loginAs(['ROLE_ACCOUNTANT'])
  return renderWithProviders(
    <BillingNewPage />,
    [`/billing/new/${bookingId}`],
    undefined,
    { path: '/billing/new/:bookingId' },
  )
}

describe('料金算出', () => {
  beforeEach(() => {
    invoices.length = 0
    server.use(...billingHandlers)
  })

  /** 法人荷主（BKG-2026000007・割引 10%）。 */
  describe('法人荷主', () => {
    it('契約割引率が自動で入る', async () => {
      renderAt('BKG-2026000007')

      expect(await screen.findByTestId('discount-rate')).toHaveTextContent('10%')
    })

    /**
     * **根拠の 4 つの係数がすべて出る**（[ADR-027] 決定 1）。
     *
     * どれか 1 つでも欠けると、経理担当者は金額の出どころを説明できない。
     */
    it('基本料金の根拠に、4 つの係数がすべて出る', async () => {
      renderAt('BKG-2026000007')

      const basis = await screen.findByTestId('charge-basis')
      expect(basis, '基準運賃が出ていない').toHaveTextContent('基準運賃')
      expect(basis, '区間係数が出ていない').toHaveTextContent('区間係数')
      expect(basis, '重量係数が出ていない').toHaveTextContent('重量係数')
      expect(basis, '貨物種別係数が出ていない').toHaveTextContent('貨物種別係数')
    })

    /**
     * **距離を持っていないことを画面が言う**（決定 1）。
     *
     * 黙っていると、受入基準どおりに距離で計算していると読まれる。
     */
    it('区間数で代替していることを画面が言う', async () => {
      renderAt('BKG-2026000007')

      expect(
        await screen.findByText(/区間数で代替/),
        '距離の代わりに区間数を使っていることを言っていない',
      ).toBeInTheDocument()
    })
  })

  /** 個人荷主（BKG-2026000008・契約なし）。 */
  describe('個人荷主', () => {
    /**
     * **割引の欄そのものを出さない**（22-3）。
     *
     * 0% を出すと「割引が 0 だった」に読め、契約が無いことと区別できない
     * （[ADR-012] が `DiscountRate` について同じ判断をしている）。
     */
    it('割引率の欄を出さない', async () => {
      renderAt('BKG-2026000008')

      await screen.findByTestId('charge-basis')
      expect(
        screen.queryByTestId('discount-rate'),
        '個人荷主に割引率の欄が出ている。契約が無いのに割引の話が始まる',
      ).not.toBeInTheDocument()
      expect(screen.getByText(/契約割引はありません/)).toBeInTheDocument()
    })
  })

  /** 誤配のある予約（BKG-2026000009）。 */
  describe('調整の根拠', () => {
    /**
     * **IT10 の `Misroute` が初めて読まれる。**
     *
     * 「残っている」と「読める」は別である——IT10 までは予約詳細にしか出ておらず、
     * 経理担当者はその画面を開けなかった。
     */
    it('誤配の記録が、外れた港の名前つきで出る', async () => {
      renderAt('BKG-2026000009')

      const evidence = await screen.findByTestId('adjustment-evidence')
      expect(evidence, '誤配の記録が出ていない').toHaveTextContent('誤配')
      expect(evidence, '外れた港が名前で出ていない').toHaveTextContent('Singapore')
    })

    /** **根拠が無いときに枠を出さない。** 毎回何か出ると、根拠のある案件が埋もれる。 */
    it('誤配も例外も無ければ、根拠の枠を出さない', async () => {
      renderAt('BKG-2026000007')

      await screen.findByTestId('charge-basis')
      expect(
        screen.queryByTestId('adjustment-evidence'),
        '根拠が無いのに枠が出ている。根拠のある案件が埋もれる',
      ).not.toBeInTheDocument()
      expect(screen.getByText(/誤配・例外の記録はありません/)).toBeInTheDocument()
    })
  })

  describe('料金調整', () => {
    /**
     * **根拠の無い調整を作らせない**（[ADR-027] 決定 6）。
     *
     * 金額だけ残ると、あとから誰も理由を言えない。
     */
    it('内容を書かずに調整を追加できない', async () => {
      renderAt('BKG-2026000007')
      await screen.findByTestId('charge-basis')

      await userEvent.type(screen.getByLabelText('調整額'), '-10000')
      await userEvent.click(screen.getByRole('button', { name: '調整を追加' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('調整の内容を入力してください')
    })

    it('金額を書かずに調整を追加できない', async () => {
      renderAt('BKG-2026000007')
      await screen.findByTestId('charge-basis')

      await userEvent.type(screen.getByLabelText('調整の内容'), '遅延による減額')
      await userEvent.click(screen.getByRole('button', { name: '調整を追加' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('調整額を数値で入力してください')
    })

    it('調整を入れると合計が変わる', async () => {
      renderAt('BKG-2026000007')
      const before = (await screen.findByTestId('total-amount')).textContent

      await userEvent.type(screen.getByLabelText('調整の内容'), '遅延による減額')
      await userEvent.type(screen.getByLabelText('調整額'), '-10000')
      await userEvent.click(screen.getByRole('button', { name: '調整を追加' }))

      expect(screen.getByTestId('total-amount'), '調整を入れても合計が変わらない')
        .not.toHaveTextContent(before ?? '')
    })
  })

  describe('確定できないとき', () => {
    /**
     * **引取が終わっていない予約は断る**（決定 5）。
     *
     * 画面で出し分けるだけでは守れない——URL を直接開かれる。
     */
    it('引取が終わっていない予約では、料金を出さない', async () => {
      renderAt('BKG-2026000001')

      expect(await screen.findByRole('alert')).toHaveTextContent('引取が終わっていない')
    })

    /** **二重請求を断る**（決定 4）。すでに発行済みなら算出画面を開かせない。 */
    it('すでに精算書が発行されている予約では、料金を出さない', async () => {
      server.use(
        http.get('/api/v1/billing/calculations/:bookingId', () =>
          HttpResponse.json(
            { message: 'この予約にはすでに精算書が発行されています' },
            { status: 409 },
          ),
        ),
      )
      renderAt('BKG-2026000007')

      expect(await screen.findByRole('alert')).toHaveTextContent('すでに精算書が発行されています')
    })
  })

  describe('確定するとき', () => {
    /** **押す前に、動かせなくなることを言う**（決定 4）。 */
    it('確定すると金額を変更できなくなることを、押す前に言う', async () => {
      renderAt('BKG-2026000007')
      await screen.findByTestId('charge-basis')

      expect(screen.getByText(/金額は変更できなくなります/)).toBeInTheDocument()
    })
  })
})
