import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { InvoiceCalculatePage } from './InvoiceCalculatePage'
import * as useBillingModule from '../features/billing/hooks/useBilling'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <InvoiceCalculatePage />
    </QueryClientProvider>,
  )
}

describe('InvoiceCalculatePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useBillingModule, 'useCalculateInvoice').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useBillingModule.useCalculateInvoice>)
    vi.spyOn(useBillingModule, 'useConfirmInvoice').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useBillingModule.useConfirmInvoice>)
  })

  it('料金算出フォームが表示される', () => {
    renderPage()
    expect(screen.getByText('輸送料金算出')).toBeInTheDocument()
    expect(screen.getByLabelText(/予約 ID/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '料金算出' })).toBeInTheDocument()
  })

  it('予約 ID 未入力で送信すると mutate が呼ばれない', () => {
    const mockMutate = vi.fn()
    vi.spyOn(useBillingModule, 'useCalculateInvoice').mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useBillingModule.useCalculateInvoice>)

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: '料金算出' }))
    expect(mockMutate).not.toHaveBeenCalled()
  })

  it('予約 ID を入力して送信すると mutate が呼ばれる', () => {
    const mockMutate = vi.fn()
    vi.spyOn(useBillingModule, 'useCalculateInvoice').mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useBillingModule.useCalculateInvoice>)

    renderPage()
    fireEvent.change(screen.getByLabelText(/予約 ID/), {
      target: { value: 'BK-001234' },
    })
    fireEvent.click(screen.getByRole('button', { name: '料金算出' }))
    expect(mockMutate).toHaveBeenCalled()
  })

  it('isPending 中はボタンが無効化される', () => {
    vi.spyOn(useBillingModule, 'useCalculateInvoice').mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
    } as unknown as ReturnType<typeof useBillingModule.useCalculateInvoice>)

    renderPage()
    expect(screen.getByRole('button', { name: '算出中...' })).toBeDisabled()
  })
})
