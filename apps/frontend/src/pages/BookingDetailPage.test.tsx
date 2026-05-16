import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { BookingDetailPage } from './BookingDetailPage'
import type { BookingListItem } from '../features/booking/types/booking'

const mocks = vi.hoisted(() => ({
  useBookingsResult: {
    data: undefined as BookingListItem[] | undefined,
    isLoading: false,
    isError: false,
  },
  handOff: {
    mutate: vi.fn() as unknown as (id: string, opts?: { onSuccess?: () => void; onError?: (e: unknown) => void }) => void,
    isPending: false,
  },
}))

vi.mock('../features/booking/hooks/useBookings', () => ({
  useBookings: () => mocks.useBookingsResult,
  useHandOffBooking: () => mocks.handOff,
  useConfirmBooking: () => ({ mutate: vi.fn(), isPending: false }),
  useIssueTracking: () => ({ mutate: vi.fn(), isPending: false }),
}))

function renderAt(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function preliminaryBooking(id: string): BookingListItem {
  return {
    bookingId: id,
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
  }
}

describe('BookingDetailPage（US06）', () => {
  it('予約詳細を表示する', () => {
    mocks.useBookingsResult = {
      data: [preliminaryBooking('b-1')],
      isLoading: false,
      isError: false,
    }
    renderAt('/bookings/b-1')
    expect(screen.getByTestId('booking-detail')).toBeInTheDocument()
    expect(screen.getByTestId('booking-status')).toHaveTextContent('PRELIMINARY')
    expect(screen.getByText('JPYOK')).toBeInTheDocument()
    expect(screen.getByText('USLAX')).toBeInTheDocument()
  })

  it('PRELIMINARY のときに「経路設計を依頼」ボタンが表示される', () => {
    mocks.useBookingsResult = {
      data: [preliminaryBooking('b-1')],
      isLoading: false,
      isError: false,
    }
    renderAt('/bookings/b-1')
    expect(screen.getByTestId('handoff-button')).toBeInTheDocument()
  })

  it('PRELIMINARY 以外の状態ではボタンが表示されない', () => {
    mocks.useBookingsResult = {
      data: [{ ...preliminaryBooking('b-1'), bookingStatus: 'ROUTING' }],
      isLoading: false,
      isError: false,
    }
    renderAt('/bookings/b-1')
    expect(screen.queryByTestId('handoff-button')).toBeNull()
  })

  it('ボタンクリックで handOff が呼ばれ、成功時にメッセージが表示される', async () => {
    const mutate = vi.fn(
      (
        _id: string,
        opts?: { onSuccess?: () => void; onError?: (e: unknown) => void },
      ) => {
        opts?.onSuccess?.()
      },
    )
    mocks.handOff = { mutate: mutate as never, isPending: false }
    mocks.useBookingsResult = {
      data: [preliminaryBooking('b-1')],
      isLoading: false,
      isError: false,
    }

    renderAt('/bookings/b-1')
    fireEvent.click(screen.getByTestId('handoff-button'))

    expect(mutate).toHaveBeenCalledWith('b-1', expect.any(Object))
    await waitFor(() => expect(screen.getByTestId('handoff-success')).toBeInTheDocument())
  })

  it('予約が見つからないときはエラーを表示する', () => {
    mocks.useBookingsResult = { data: [], isLoading: false, isError: false }
    renderAt('/bookings/missing')
    expect(screen.getByText(/予約が見つかりません/)).toBeInTheDocument()
  })
})
