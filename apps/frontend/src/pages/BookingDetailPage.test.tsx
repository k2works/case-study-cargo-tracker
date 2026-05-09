import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BookingDetailPage } from './BookingDetailPage'
import * as useBookingsModule from '../features/booking/hooks/useBookings'
import * as useTrackingModule from '../features/tracking/hooks/useTracking'

function renderPage(bookingId = 'BK-001') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/bookings/${bookingId}`]}>
        <Routes>
          <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const baseCargo = {
  bookingId: 'BK-001',
  shipperId: 1,
  originUnlocode: 'JPTYO',
  destinationUnlocode: 'CNSHA',
  arrivalDeadline: null,
  weightKg: 100,
  bookingStatus: 'PRELIMINARY' as const,
  trackingNumber: null,
  itinerary: null,
}

describe('BookingDetailPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useTrackingModule, 'useIssueTrackingNumber').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useTrackingModule.useIssueTrackingNumber>)
    vi.spyOn(useBookingsModule, 'useConfirmBooking').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useBookingsModule.useConfirmBooking>)
    vi.spyOn(useBookingsModule, 'useCancelBooking').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useBookingsModule.useCancelBooking>)
  })

  it('ROUTE_PROPOSED 状態では「経路通知送信済み」バッジが表示されない', () => {
    vi.spyOn(useBookingsModule, 'useBooking').mockReturnValue({
      data: { ...baseCargo, bookingStatus: 'ROUTE_PROPOSED' },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useBookingsModule.useBooking>)

    renderPage()
    expect(screen.queryByText(/経路通知送信済み/)).not.toBeInTheDocument()
  })

  it('CONFIRMED 状態では「経路通知送信済み」バッジが表示される', () => {
    vi.spyOn(useBookingsModule, 'useBooking').mockReturnValue({
      data: { ...baseCargo, bookingStatus: 'CONFIRMED' },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useBookingsModule.useBooking>)

    renderPage()
    expect(screen.getByText(/経路通知送信済み/)).toBeInTheDocument()
  })

  it('PRELIMINARY 状態では「経路通知送信済み」バッジが表示されない', () => {
    vi.spyOn(useBookingsModule, 'useBooking').mockReturnValue({
      data: { ...baseCargo, bookingStatus: 'PRELIMINARY' },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useBookingsModule.useBooking>)

    renderPage()
    expect(screen.queryByText(/経路通知送信済み/)).not.toBeInTheDocument()
  })
})
