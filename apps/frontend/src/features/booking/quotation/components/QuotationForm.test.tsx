import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QuotationForm } from './QuotationForm'

const mutateMock = vi.fn()
let pendingState = false
let errorState = false

vi.mock('../hooks/useQuotations', () => ({
  useCreateQuotation: () => ({
    mutate: mutateMock,
    get isPending() {
      return pendingState
    },
    get isError() {
      return errorState
    },
    error: null,
  }),
}))

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <QuotationForm />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('QuotationForm', () => {
  beforeEach(() => {
    mutateMock.mockReset()
    pendingState = false
    errorState = false
  })

  it('受入条件 1: 出発地・目的地・希望期限・貨物種別・重量の入力欄が表示される', () => {
    renderForm()
    expect(screen.getByLabelText('荷主 ID')).toBeInTheDocument()
    expect(screen.getByLabelText('出発地（UN/LOCODE）')).toBeInTheDocument()
    expect(screen.getByLabelText('目的地（UN/LOCODE）')).toBeInTheDocument()
    expect(screen.getByLabelText('希望期限')).toBeInTheDocument()
    expect(screen.getByLabelText('貨物種別')).toBeInTheDocument()
    expect(screen.getByLabelText('重量 (kg)')).toBeInTheDocument()
  })

  it('受入条件 6: 一般貨物の初期状態では危険物申告フォームは表示されない', () => {
    renderForm()
    expect(screen.queryByTestId('hazard-info-fieldset')).not.toBeInTheDocument()
  })

  it('受入条件 6: 貨物種別を HAZARDOUS にすると危険物申告フォームが表示される', async () => {
    renderForm()
    const cargoType = screen.getByLabelText('貨物種別') as HTMLSelectElement
    await userEvent.selectOptions(cargoType, 'HAZARDOUS')
    expect(screen.getByTestId('hazard-info-fieldset')).toBeInTheDocument()
    expect(screen.getByLabelText('IMO クラス')).toBeInTheDocument()
    expect(screen.getByLabelText('UN 番号')).toBeInTheDocument()
    expect(screen.getByLabelText('申告内容')).toBeInTheDocument()
  })

  it('「見積を作成」ボタンで useCreateQuotation.mutate が呼ばれる', async () => {
    renderForm()
    await userEvent.type(screen.getByLabelText('荷主 ID'), '1')
    await userEvent.type(screen.getByLabelText('出発地（UN/LOCODE）'), 'JPTYO')
    await userEvent.type(screen.getByLabelText('目的地（UN/LOCODE）'), 'USNYC')
    await userEvent.type(screen.getByLabelText('希望期限'), '2026-12-31')
    await userEvent.type(screen.getByLabelText('重量 (kg)'), '100')

    await userEvent.click(screen.getByRole('button', { name: '見積を作成' }))

    expect(mutateMock).toHaveBeenCalledTimes(1)
    const [arg] = mutateMock.mock.calls[0]
    expect(arg).toMatchObject({
      shipperId: 1,
      originUnLocode: 'JPTYO',
      destinationUnLocode: 'USNYC',
      arrivalDeadline: '2026-12-31',
      cargoType: 'GENERAL',
      weightKg: 100,
      hazardInfo: null,
    })
  })
})
