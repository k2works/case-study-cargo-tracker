import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BookingListPage } from './BookingListPage'
import * as useBookingsModule from '../features/booking/hooks/useBookings'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <BookingListPage />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('BookingListPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useBookingsModule, 'useBookings').mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useBookingsModule.useBookings>)
  })

  it('ページタイトルが表示される', () => {
    renderPage()
    expect(screen.getByText('貨物予約管理')).toBeInTheDocument()
  })

  it('新規予約リンクが表示される', () => {
    renderPage()
    expect(screen.getByRole('link', { name: '新規予約' })).toBeInTheDocument()
  })

  it('予約がない場合メッセージが表示される', () => {
    renderPage()
    expect(screen.getByText('予約が登録されていません。')).toBeInTheDocument()
  })
})
