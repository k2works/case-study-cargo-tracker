import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TrackingExceptionPage } from './TrackingExceptionPage'
import * as useTrackingModule from '../features/tracking/hooks/useTracking'

const mockActivity = {
  trackingNumber: 'TRK-000001',
  bookingId: 'BK-001234',
  transportStatus: 'LOADED' as const,
  events: [],
  exceptions: [],
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/tracking/TRK-000001/exceptions']}>
        <Routes>
          <Route path="/tracking/:trackingNumber/exceptions" element={<TrackingExceptionPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TrackingExceptionPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useTrackingModule, 'useTrackingActivity').mockReturnValue({
      data: mockActivity,
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingActivity>)
    vi.spyOn(useTrackingModule, 'useRecordTrackingException').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useTrackingModule.useRecordTrackingException>)
  })

  it('遅延例外記録フォームが表示される', () => {
    renderPage()
    expect(screen.getByText('遅延例外記録')).toBeInTheDocument()
    expect(screen.getByLabelText(/例外種別/)).toBeInTheDocument()
    expect(screen.getByLabelText(/遅延・例外理由/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '例外を記録する' })).toBeInTheDocument()
  })

  it('理由未入力で送信するとエラーになる', () => {
    const mockMutate = vi.fn()
    vi.spyOn(useTrackingModule, 'useRecordTrackingException').mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useTrackingModule.useRecordTrackingException>)

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: '例外を記録する' }))
    expect(mockMutate).not.toHaveBeenCalled()
  })

  it('理由を入力して送信すると mutate が呼ばれる', () => {
    const mockMutate = vi.fn()
    vi.spyOn(useTrackingModule, 'useRecordTrackingException').mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useTrackingModule.useRecordTrackingException>)

    renderPage()
    fireEvent.change(screen.getByLabelText(/遅延・例外理由/), {
      target: { value: '悪天候による遅延' },
    })
    fireEvent.click(screen.getByRole('button', { name: '例外を記録する' }))
    expect(mockMutate).toHaveBeenCalled()
  })

  it('isPending 中はボタンが無効化される', () => {
    vi.spyOn(useTrackingModule, 'useRecordTrackingException').mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
    } as unknown as ReturnType<typeof useTrackingModule.useRecordTrackingException>)

    renderPage()
    expect(screen.getByRole('button', { name: '記録中...' })).toBeDisabled()
  })
})
