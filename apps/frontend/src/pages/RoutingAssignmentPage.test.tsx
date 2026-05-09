import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RoutingAssignmentPage } from './RoutingAssignmentPage'
import * as useBookingsModule from '../features/booking/hooks/useBookings'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <RoutingAssignmentPage />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('RoutingAssignmentPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('ページタイトルが表示される', () => {
    vi.spyOn(useBookingsModule, 'useRoutingAssignments').mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useBookingsModule.useRoutingAssignments>)

    renderPage()
    expect(screen.getByText('経路設計担当一覧')).toBeInTheDocument()
  })

  it('担当案件がない場合メッセージが表示される', () => {
    vi.spyOn(useBookingsModule, 'useRoutingAssignments').mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useBookingsModule.useRoutingAssignments>)

    renderPage()
    expect(screen.getByText('経路設計待ちの予約はありません。')).toBeInTheDocument()
  })

  it('担当案件が一覧表示される', () => {
    vi.spyOn(useBookingsModule, 'useRoutingAssignments').mockReturnValue({
      data: [
        {
          bookingId: 'BOOKING001',
          shipperId: 1,
          bookingStatus: 'CONFIRMED',
          cargoType: 'GENERAL',
          weightKg: 100,
          originUnlocode: 'JPTYO',
          destinationUnlocode: 'CNSHA',
          arrivalDeadline: '2026-08-01',
          legs: [],
        },
      ],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useBookingsModule.useRoutingAssignments>)

    renderPage()
    expect(screen.getByText('BOOKING001')).toBeInTheDocument()
    expect(screen.getByText('JPTYO')).toBeInTheDocument()
    expect(screen.getByText('CNSHA')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '経路設計' })).toBeInTheDocument()
  })
})
