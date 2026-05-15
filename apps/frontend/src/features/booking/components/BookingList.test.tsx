import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { BookingList } from './BookingList'
import type { BookingListItem } from '../types/booking'

const mocks = vi.hoisted(() => ({
  useBookingsResult: {
    data: undefined as BookingListItem[] | undefined,
    isLoading: false,
    isError: false,
  },
}))

vi.mock('../hooks/useBookings', () => ({
  useBookings: () => mocks.useBookingsResult,
}))

function renderBookingList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <BookingList />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('BookingList', () => {
  it('読み込み中はメッセージを表示する', () => {
    mocks.useBookingsResult = { data: undefined, isLoading: true, isError: false }
    renderBookingList()
    expect(screen.getByText('読み込み中...')).toBeInTheDocument()
  })

  it('エラー時はエラーメッセージを表示する', () => {
    mocks.useBookingsResult = { data: undefined, isLoading: false, isError: true }
    renderBookingList()
    expect(screen.getByText('データの取得に失敗しました。')).toBeInTheDocument()
  })

  it('データが空のときは案内文を表示する', () => {
    mocks.useBookingsResult = { data: [], isLoading: false, isError: false }
    renderBookingList()
    expect(screen.getByText('予約が登録されていません。')).toBeInTheDocument()
  })

  it('予約一覧をテーブル形式で表示する', () => {
    mocks.useBookingsResult = {
      data: [
        {
          bookingId: 'b-1',
          shipperId: 1,
          trackingNumber: null,
          cargoType: 'GENERAL',
          productName: '産業機械',
          originUnLocode: 'JPYOK',
          destinationUnLocode: 'USLAX',
          arrivalDeadline: '2099-12-31',
          bookingStatus: 'PRELIMINARY',
          routingStatus: 'NOT_ROUTED',
          createdAt: '2026-05-15T10:00:00',
        },
      ],
      isLoading: false,
      isError: false,
    }
    renderBookingList()
    expect(screen.getByText('b-1')).toBeInTheDocument()
    expect(screen.getByText('産業機械')).toBeInTheDocument()
    expect(screen.getByText('JPYOK')).toBeInTheDocument()
    expect(screen.getByText('USLAX')).toBeInTheDocument()
    expect(screen.getByText('PRELIMINARY')).toBeInTheDocument()
  })
})
