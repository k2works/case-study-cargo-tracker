import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import { billingHandlers, invoices } from '../../mocks/handlers/billing'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { BillingPage } from '../billing-page'

/**
 * 精算管理（US21-1）。
 *
 * <p><strong>経理担当者が毎朝開く画面である。</strong>ここに出ていないものは、
 * 誰にも気づかれないまま滞留する（通知の仕組みは US23 以降）。
 */
/**
 * 種の請求書を、**消される前に**控える。
 *
 * <p>各テストは `invoices` を空にしてから始める。空にしたあとで写しを作ると
 * <strong>項目の欠けた請求書</strong>ができ、表の他の列が空のまま緑になる
 * ——モックが本物より甘い状態である。
 */
const SEED_INVOICE = structuredClone(invoices[0])

/** 検索を確かめるための請求書。**金額を変えて、合計が足し算であることを見る**。 */
function sampleInvoice(
  invoiceNumber: string,
  bookingId: string,
  shipperName: string,
  total: number,
) {
  return {
    ...SEED_INVOICE,
    invoiceId: invoiceNumber,
    invoiceNumber,
    bookingId,
    shipperName,
    totalAmount: { value: total, currency: 'JPY' },
    issuedAt: '2026-06-05T00:00:00Z',
    voidedAt: null,
  }
}

function renderPage() {
  loginAs(['ROLE_ACCOUNTANT'])
  return renderWithProviders(<BillingPage />, ['/billing'])
}

describe('精算管理', () => {
  beforeEach(() => {
    invoices.length = 0
    server.use(...billingHandlers)
  })

  /**
   * <strong>引取が終わった順に並ぶ。</strong>
   *
   * <p>待たせている案件が上に来る——新しい順だと、いちばん待たせている荷主への請求が
   * 下に沈む。
   */
  it('引取が終わった順に並ぶ', async () => {
    renderPage()

    await screen.findByRole('heading', { name: '精算管理' })
    const rows = await screen.findAllByRole('row')
    const bookingIds = rows
      .map((row) => row.textContent ?? '')
      .filter((text) => text.includes('BKG-'))

    expect(bookingIds[0], '待たせている案件が上に来ていない')
      .toContain('BKG-2026000010')
  })

  /**
   * <strong>列の名前と中身を揃える</strong>（IT11 レビュー 中）。
   *
   * <p>出しているのは<strong>最後に荷役があった日時</strong>であり、引取の日時とは
   * 限らない——キャンセルされた予約は引き取っていないが、途中まで運ばれていれば
   * 荷役の記録を持つ。一覧の並びもこの値で決まるので、名前が「引取日時」だと
   * 「引取の順に並んでいる」と読まれる。
   */
  it('列の名前が「最終荷役日時」である', async () => {
    renderPage()

    await screen.findByRole('heading', { name: '精算管理' })

    expect(
      await screen.findByRole('columnheader', { name: '最終荷役日時' }),
      '列名が中身と食い違っている',
    ).toBeInTheDocument()
  })

  /** 荷役の記録がある予約には日時が出る。 */
  it('荷役の記録がある予約には、日時が出る', async () => {
    renderPage()

    await screen.findByRole('heading', { name: '精算管理' })
    const corporate = (await screen.findAllByTestId('unbilled-corporate'))[0]

    expect(within(corporate).getByText(/2027-/)).toBeInTheDocument()
  })

  /** **根拠のある案件は先に知らせる。** 開いてから気づくと、判断し直すことになる。 */
  it('誤配とキャンセルを、一覧の時点で示す', async () => {
    renderPage()

    await screen.findByRole('heading', { name: '精算管理' })

    expect(within((await screen.findAllByTestId('unbilled-misrouted'))[0])
      .getByText('誤配あり')).toBeInTheDocument()
    expect(within((await screen.findAllByTestId('unbilled-cancelled'))[0])
      .getByText('キャンセル')).toBeInTheDocument()
  })

  /** 発行済みが無いときは、その旨を出す。**空の表を出さない。** */
  it('発行済みの精算書が無ければ、その旨を出す', async () => {
    renderPage()

    expect(await screen.findByText('発行済みの精算書はありません。')).toBeInTheDocument()
  })

  /**
   * <strong>締めの作業を表計算から引き上げる</strong>（US38）。
   *
   * <p>4 度目の申し送りであり、経理担当者から 2 IT 連続の指摘を受けていた。
   */
  describe('請求書を探す', () => {
    beforeEach(() => {
      invoices.push(sampleInvoice('INV-2026000901', 'BKG-2026000101', '伊藤商事', 55_000))
      invoices.push(sampleInvoice('INV-2026000902', 'BKG-2026000102', '大洋物産', 33_000))
    })

    it('荷主名で絞り込める', async () => {
      renderPage()
      await screen.findByRole('heading', { name: '精算管理' })

      await userEvent.type(
        screen.getByLabelText('請求番号・荷主名・予約番号'),
        '伊藤',
      )

      await waitFor(() => {
        expect(screen.queryByText('INV-2026000902')).not.toBeInTheDocument()
      })
      expect(screen.getByText('INV-2026000901')).toBeInTheDocument()
    })

    /**
     * <strong>合計はサーバが数える。</strong>画面で足し上げると、上限で切った瞬間に
     * 「見えている分だけの合計」に化ける。
     */
    it('件数と合計を出す', async () => {
      renderPage()
      await screen.findByRole('heading', { name: '精算管理' })

      expect(await screen.findByText('¥88,000')).toBeInTheDocument()
      expect(screen.getByText('（取り消し済みを除く）')).toBeInTheDocument()
    })

    it('条件を消せる', async () => {
      renderPage()
      await screen.findByRole('heading', { name: '精算管理' })

      await userEvent.type(screen.getByLabelText('請求番号・荷主名・予約番号'), '伊藤')
      await waitFor(() => {
        expect(screen.queryByText('INV-2026000902')).not.toBeInTheDocument()
      })

      await userEvent.click(screen.getByRole('button', { name: '条件を消す' }))

      expect(await screen.findByText('INV-2026000902')).toBeInTheDocument()
    })
  })
})
