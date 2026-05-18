import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RoutingWorkbenchPage } from './RoutingWorkbenchPage'
import type { BookingListItem } from '../features/booking/types/booking'

const ROUTING_BOOKING: BookingListItem = {
  bookingId: 'BK-TEST-001',
  bookingStatus: 'ROUTING',
  originUnLocode: 'JPYOK',
  destinationUnLocode: 'USLAX',
  arrivalDeadline: '2099-12-31',
  cargoType: 'GENERAL',
  shipperId: 1,
  productName: 'テスト貨物',
  trackingNumber: null,
  routingStatus: null,
  createdAt: null,
}

const mocks = vi.hoisted(() => ({
  useBookingsResult: {
    data: [] as BookingListItem[],
    isLoading: false,
    isError: false,
  },
  assignRoute: {
    mutate: vi.fn(),
    isPending: false,
  },
  fetchRouteCandidates: vi.fn(),
  adjustRouteCandidates: vi.fn(),
  selectRoute: vi.fn(),
}))

vi.mock('../features/booking/hooks/useBookings', () => ({
  useBookings: () => mocks.useBookingsResult,
}))

vi.mock('../features/routing/workbench/hooks/useWorkbench', () => ({
  fetchRouteCandidates: mocks.fetchRouteCandidates,
  adjustRouteCandidates: mocks.adjustRouteCandidates,
  selectRoute: mocks.selectRoute,
  useAssignRoute: () => mocks.assignRoute,
}))

function renderWorkbench() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/routing/workbench/BK-TEST-001']}>
        <Routes>
          <Route path="/routing/workbench/:bookingId" element={<RoutingWorkbenchPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RoutingWorkbenchPage US10: 条件調整・再算出', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useBookingsResult.data = [ROUTING_BOOKING]
  })

  it('候補ゼロ時に条件調整フォームを表示する', async () => {
    mocks.fetchRouteCandidates.mockResolvedValue({ candidates: [] })

    renderWorkbench()
    fireEvent.click(screen.getByTestId('search-candidates-button'))

    await waitFor(() => {
      expect(screen.getByTestId('adjust-conditions-form')).toBeInTheDocument()
    })
  })

  it('条件調整フォームで再算出ボタンをクリックすると adjustRouteCandidates が呼ばれる', async () => {
    mocks.fetchRouteCandidates.mockResolvedValue({ candidates: [] })
    mocks.adjustRouteCandidates.mockResolvedValue({ candidates: [], noRoutesMessage: '担当者にご連絡ください。' })

    renderWorkbench()
    fireEvent.click(screen.getByTestId('search-candidates-button'))

    await waitFor(() => {
      expect(screen.getByTestId('adjust-conditions-form')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByTestId('adjust-search-button'))

    await waitFor(() => {
      expect(mocks.adjustRouteCandidates).toHaveBeenCalled()
    })
  })

  it('再算出後も候補ゼロの場合、営業担当者向けメッセージを表示する', async () => {
    mocks.fetchRouteCandidates.mockResolvedValue({ candidates: [] })
    mocks.adjustRouteCandidates.mockResolvedValue({
      candidates: [],
      noRoutesMessage: '担当者にご連絡ください。',
    })

    renderWorkbench()
    fireEvent.click(screen.getByTestId('search-candidates-button'))

    await waitFor(() => screen.getByTestId('adjust-conditions-form'))
    fireEvent.click(screen.getByTestId('adjust-search-button'))

    await waitFor(() => {
      expect(screen.getByTestId('no-routes-message')).toBeInTheDocument()
    })
  })

  it('再算出で候補が見つかれば候補一覧を表示する', async () => {
    mocks.fetchRouteCandidates.mockResolvedValue({ candidates: [] })
    mocks.adjustRouteCandidates.mockResolvedValue({
      candidates: [{ voyageNumbers: ['V999'], via: [], transitDays: 14 }],
      noRoutesMessage: null,
    })

    renderWorkbench()
    fireEvent.click(screen.getByTestId('search-candidates-button'))

    await waitFor(() => screen.getByTestId('adjust-conditions-form'))
    fireEvent.click(screen.getByTestId('adjust-search-button'))

    await waitFor(() => {
      expect(screen.getByTestId('candidates-section')).toBeInTheDocument()
    })
  })
})
