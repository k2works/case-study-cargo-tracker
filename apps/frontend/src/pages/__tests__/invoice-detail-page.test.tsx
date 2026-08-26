import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import { billingHandlers, invoices } from '../../mocks/handlers/billing'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { InvoiceDetailPage } from '../invoice-detail-page'

/**
 * 請求書詳細（US21-5・US22-4）。
 *
 * <p>確かめるのは **E2E では判別しにくいこと**——発行後に金額を動かす操作が
 * <strong>残っていない</strong>こと（[ADR-027] 決定 4）。
 * 「操作が無い」ことは実演では示しにくい。
 */
const ISSUED = {
  invoiceId: 'INV-2026000001',
  invoiceNumber: 'INV-2026000001',
  bookingId: 'BKG-2026000007',
  shipperName: '丸紅商事株式会社',
  basis: {
    baseFare: { value: 50_000, currency: 'JPY' },
    legCount: 2,
    legFactor: 2,
    weightKg: 4200,
    weightFactor: 4.2,
    cargoType: 'GENERAL',
    cargoTypeLabel: '一般貨物',
    cargoTypeFactor: 1,
  },
  baseAmount: { value: 420_000, currency: 'JPY' },
  discountRate: 0.1,
  discountAmount: { value: 42_000, currency: 'JPY' },
  lineItems: [{ description: '遅延による減額', amount: { value: -10_000, currency: 'JPY' } }],
  cancellationFee: null,
  taxRate: 0.1,
  taxAmount: { value: 36_800, currency: 'JPY' },
  totalAmount: { value: 404_800, currency: 'JPY' },
  paymentStatus: 'PENDING',
  issuedAt: '2027-10-01T00:00:00Z',
  dueDate: null,
}

function renderInvoice(invoiceId = ISSUED.invoiceId) {
  loginAs(['ROLE_ACCOUNTANT'])
  return renderWithProviders(
    <InvoiceDetailPage />,
    [`/billing/${invoiceId}`],
    undefined,
    { path: '/billing/:invoiceId' },
  )
}

describe('請求書詳細', () => {
  beforeEach(() => {
    invoices.length = 0
    invoices.push({ ...ISSUED } as never)
    server.use(...billingHandlers)
  })

  /**
   * **金額を動かす操作を置かない**（[ADR-027] 決定 4）。
   *
   * 請求書は荷主へ出す約束であり、出したあとに黙って変わると請求の根拠が消える。
   */
  it('金額を動かす操作が残っていない', async () => {
    renderInvoice()

    const breakdown = await screen.findByTestId('amount-breakdown')
    expect(within(breakdown).queryByRole('button')).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /調整を追加|確定する/ }),
      '発行後も金額を動かす操作が残っている。請求の根拠が消える',
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  /** **割引率を出す**（22-4）。額だけでは率を復元できない——割り戻すと丸めの分ずれる。 */
  it('金額内訳に割引の根拠が載る', async () => {
    renderInvoice()

    const breakdown = await screen.findByTestId('amount-breakdown')
    expect(breakdown, '基本運賃が出ていない').toHaveTextContent('基本運賃')
    expect(breakdown, '割引率が出ていない').toHaveTextContent('法人割引（10%）')
    expect(breakdown, '割引額が出ていない').toHaveTextContent('-¥42,000')
    expect(breakdown, '割引後の金額が出ていない').toHaveTextContent('¥378,000')
    expect(breakdown, '消費税が出ていない').toHaveTextContent('消費税（10%）')
    expect(breakdown, '合計が出ていない').toHaveTextContent('¥404,800')
  })

  /** 調整の明細は根拠つきで残る（決定 6）。**金額だけ残ると、あとから誰も理由を言えない。** */
  it('料金調整が、内容つきで明細に残る', async () => {
    renderInvoice()

    const breakdown = await screen.findByTestId('amount-breakdown')
    expect(breakdown).toHaveTextContent('遅延による減額')
    expect(breakdown).toHaveTextContent('-¥10,000')
  })

  /** **通知は代替である。** 書かないと「発行したから届いた」と受け取られる。 */
  it('荷主へ自動で通知されないことを言う', async () => {
    renderInvoice()

    expect(await screen.findByText(/荷主へは自動で通知されません/)).toBeInTheDocument()
  })

  /** **発行の時点では未入金**（決定 3）。入金の確認は US23（IT12）。 */
  it('発行直後の状態は未入金である', async () => {
    renderInvoice()

    expect(await screen.findByTestId('payment-status')).toHaveTextContent('未入金')
  })

  it('見つからない精算書では、その旨と戻る導線を出す', async () => {
    renderInvoice('INV-9999999999')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('精算書が見つかりません')
    expect(within(alert).getByRole('link', { name: '精算管理へ戻る' })).toBeInTheDocument()
  })
})
