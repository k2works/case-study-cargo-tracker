import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { QuotationList } from './QuotationList'
import type { QuotationListItem } from '../types/quotation'

const sampleData: QuotationListItem[] = [
  {
    quotationId: 'Q-LIST-001',
    shipperId: 1,
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    arrivalDeadline: '2026-08-31',
    cargoType: 'GENERAL',
    weightKg: 100,
    estimatedAmount: 100000,
    estimatedCurrency: 'JPY',
    validUntil: '2026-05-22',
    status: 'OFFERED',
  },
  {
    quotationId: 'Q-LIST-002',
    shipperId: 2,
    originUnLocode: 'JPOSA',
    destinationUnLocode: 'DEHAM',
    arrivalDeadline: '2026-09-30',
    cargoType: 'HAZARDOUS',
    weightKg: 50,
    estimatedAmount: 65000,
    estimatedCurrency: 'JPY',
    validUntil: '2026-05-22',
    status: 'DRAFT',
  },
]

let mockData: QuotationListItem[] | undefined = sampleData
let mockLoading = false
let mockError = false

vi.mock('../hooks/useQuotations', () => ({
  useQuotations: () => ({
    get data() {
      return mockData
    },
    get isLoading() {
      return mockLoading
    },
    get isError() {
      return mockError
    },
  }),
}))

function renderList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <QuotationList />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('QuotationList', () => {
  it('受入条件 4: 見積番号と状態を一覧で確認できる', () => {
    mockData = sampleData
    mockLoading = false
    mockError = false
    renderList()
    expect(screen.getByText('Q-LIST-001')).toBeInTheDocument()
    expect(screen.getByText('Q-LIST-002')).toBeInTheDocument()
    expect(screen.getByText('OFFERED')).toBeInTheDocument()
    expect(screen.getByText('DRAFT')).toBeInTheDocument()
  })

  it('各行に詳細リンクが表示され S04 を指す', () => {
    mockData = sampleData
    mockLoading = false
    mockError = false
    renderList()
    const link = screen.getByTestId('detail-link-Q-LIST-001')
    expect(link).toHaveAttribute('href', '/quotations/Q-LIST-001')
  })

  it('データなしの場合「見積が登録されていません」を表示する', () => {
    mockData = []
    mockLoading = false
    mockError = false
    renderList()
    expect(screen.getByText('見積が登録されていません。')).toBeInTheDocument()
  })

  it('ローディング中はメッセージを表示する', () => {
    mockData = undefined
    mockLoading = true
    mockError = false
    renderList()
    expect(screen.getByText('読み込み中...')).toBeInTheDocument()
  })
})
