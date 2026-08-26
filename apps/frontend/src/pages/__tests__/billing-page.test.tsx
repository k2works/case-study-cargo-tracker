import { screen, within } from '@testing-library/react'
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
   * <strong>キャンセルされた予約に「引取日時」を出さない。</strong>
   *
   * <p>引き取っていないのに引取日時があると、経理担当者は「引き取ったのにキャンセル
   * された」と読む。並びに使っているのは最後に荷役があった日時であり、
   * <strong>引取日時とは別のものである</strong>。
   */
  it('キャンセルされた予約には、引取日時を出さない', async () => {
    renderPage()

    await screen.findByRole('heading', { name: '精算管理' })
    const cancelled = (await screen.findAllByTestId('unbilled-cancelled'))[0]

    expect(
      within(cancelled).getByText('—'),
      '引き取っていない予約に引取日時が出ている',
    ).toBeInTheDocument()
  })

  /** 引取済の予約には引取日時が出る。 */
  it('引取済の予約には、引取日時が出る', async () => {
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
})
