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

  it('イベント履歴の状態列は各イベント種別に対応した状態を表示する', () => {
    vi.spyOn(useTrackingModule, 'useTrackingQuery').mockReturnValue({
      data: {
        trackingNumber: 'TRK-000001',
        bookingId: 'BK-001234',
        transportStatus: 'LOADED',
        events: [
          {
            eventType: 'RECEIVE',
            locationUnlocode: 'JPTYO',
            eventTime: '2026-01-01T10:00:00',
            voyageNumber: null,
          },
          {
            eventType: 'LOAD',
            locationUnlocode: 'JPTYO',
            eventTime: '2026-01-02T10:00:00',
            voyageNumber: 'V0001',
          },
        ],
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrackingModule.useTrackingQuery>)

    renderPage()
    fireEvent.change(screen.getByPlaceholderText(/TRK-/), {
      target: { value: 'TRK-000001' },
    })
    fireEvent.submit(screen.getByRole('button', { name: '追跡する' }))

    // RECEIVE イベント行は「受領済み」を表示すること（現在の状態は積込済みなので、受領済みはイベント行のみ）
    expect(screen.getByText('受領済み')).toBeInTheDocument()
    // LOAD イベント行は「積込済み」を表示すること（現在の状態も積込済みなので複数存在する）
    expect(screen.getAllByText('積込済み').length).toBeGreaterThanOrEqual(2)
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
