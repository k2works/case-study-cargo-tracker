import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TrackingStatusPage } from './TrackingStatusPage'
import * as useTrackingModule from '../features/tracking/hooks/useTracking'

function renderPage(trackingNumber = 'TRK-000001') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/tracking/${trackingNumber}/status`]}>
        <Routes>
          <Route path="/tracking/:trackingNumber/status" element={<TrackingStatusPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TrackingStatusPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useTrackingModule, 'useUpdateTrackingStatus').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useTrackingModule.useUpdateTrackingStatus>)
  })

  it('ページタイトルが表示される', () => {
    vi.spyOn(useTrackingModule, 'useTrackingActivity').mockReturnValue({
      data: {
        trackingNumber: 'TRK-000001',
        bookingId: 'BK-001',
        transportStatus: 'RECEIVED',
        events: [],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingActivity>)

    renderPage()
    expect(screen.getByText('追跡状態更新')).toBeInTheDocument()
  })

  it('現在が LOADED の場合、RECEIVED（逆行状態）は無効化されている', () => {
    vi.spyOn(useTrackingModule, 'useTrackingActivity').mockReturnValue({
      data: {
        trackingNumber: 'TRK-000001',
        bookingId: 'BK-001',
        transportStatus: 'LOADED',
        events: [],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingActivity>)

    renderPage()
    const receivedOption = screen.getByRole('option', { name: '受領済み' })
    expect(receivedOption).toBeDisabled()
  })

  it('現在が LOADED の場合、ONBOARD_CARRIER（順行状態）は有効化されている', () => {
    vi.spyOn(useTrackingModule, 'useTrackingActivity').mockReturnValue({
      data: {
        trackingNumber: 'TRK-000001',
        bookingId: 'BK-001',
        transportStatus: 'LOADED',
        events: [],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingActivity>)

    renderPage()
    const onboardOption = screen.getByRole('option', { name: '輸送中' })
    expect(onboardOption).not.toBeDisabled()
  })

  it('EXCEPTION は現在の状態に関係なく選択可能', () => {
    vi.spyOn(useTrackingModule, 'useTrackingActivity').mockReturnValue({
      data: {
        trackingNumber: 'TRK-000001',
        bookingId: 'BK-001',
        transportStatus: 'CLAIMED',
        events: [],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingActivity>)

    renderPage()
    const exceptionOption = screen.getByRole('option', { name: '例外' })
    expect(exceptionOption).not.toBeDisabled()
  })
})
