import { render, screen, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TrackingPage } from './TrackingPage'
import * as useTrackingModule from '../features/tracking/hooks/useTracking'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <TrackingPage />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('TrackingPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('ページタイトルが表示される', () => {
    renderPage()
    expect(screen.getByText('貨物追跡照会')).toBeInTheDocument()
  })

  it('追跡番号入力フォームが表示される', () => {
    renderPage()
    expect(screen.getByPlaceholderText(/TRK-/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '追跡する' })).toBeInTheDocument()
  })

  it('初期状態では追跡結果が表示されない', () => {
    renderPage()
    expect(screen.queryByText('現在の状態')).not.toBeInTheDocument()
  })

  it('追跡番号を入力して検索すると結果エリアが表示される', () => {
    vi.spyOn(useTrackingModule, 'useTrackingQuery').mockReturnValue({
      data: {
        trackingNumber: 'TRK-000001',
        bookingId: 'BK-001234',
        transportStatus: 'RECEIVED',
        events: [],
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingQuery>)

    renderPage()
    fireEvent.change(screen.getByPlaceholderText(/TRK-/), {
      target: { value: 'TRK-000001' },
    })
    fireEvent.submit(screen.getByRole('button', { name: '追跡する' }))

    expect(screen.getByText('現在の状態')).toBeInTheDocument()
    expect(screen.getByText('TRK-000001')).toBeInTheDocument()
  })

  it('存在しない追跡番号は404メッセージを表示する', () => {
    vi.spyOn(useTrackingModule, 'useTrackingQuery').mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingQuery>)

    renderPage()
    fireEvent.change(screen.getByPlaceholderText(/TRK-/), {
      target: { value: 'TRK-999999' },
    })
    fireEvent.submit(screen.getByRole('button', { name: '追跡する' }))

    expect(screen.getByText(/追跡番号が見つかりません/)).toBeInTheDocument()
  })

  it('例外状態の場合に赤色バッジが表示される', () => {
    vi.spyOn(useTrackingModule, 'useTrackingQuery').mockReturnValue({
      data: {
        trackingNumber: 'TRK-000002',
        bookingId: 'BK-001235',
        transportStatus: 'EXCEPTION',
        events: [],
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingQuery>)

    renderPage()
    fireEvent.change(screen.getByPlaceholderText(/TRK-/), {
      target: { value: 'TRK-000002' },
    })
    fireEvent.submit(screen.getByRole('button', { name: '追跡する' }))

    expect(screen.getByText('例外あり')).toBeInTheDocument()
  })
})
