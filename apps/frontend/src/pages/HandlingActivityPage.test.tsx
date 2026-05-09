import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { HandlingActivityPage } from './HandlingActivityPage'
import * as useTrackingModule from '../features/tracking/hooks/useTracking'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <HandlingActivityPage />
    </QueryClientProvider>,
  )
}

describe('HandlingActivityPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('ページタイトルが表示される', () => {
    vi.spyOn(useTrackingModule, 'useRecordHandlingActivity').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useTrackingModule.useRecordHandlingActivity>)

    renderPage()
    expect(screen.getByText('荷役作業記録')).toBeInTheDocument()
  })

  it('荷役記録成功後に追跡番号フィールドが保持される', async () => {
    const mockMutate = vi.fn((_data, callbacks) => {
      callbacks.onSuccess({ trackingNumber: 'TRK-000001', transportStatus: 'RECEIVED' })
    })
    vi.spyOn(useTrackingModule, 'useRecordHandlingActivity').mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useTrackingModule.useRecordHandlingActivity>)

    renderPage()

    // フォームに入力
    fireEvent.change(screen.getByPlaceholderText('TRK-000001'), { target: { value: 'TRK-000001' } })
    fireEvent.change(screen.getByPlaceholderText('JPTYO'), { target: { value: 'JPTYO' } })
    fireEvent.change(screen.getByDisplayValue('受領'), { target: { value: 'RECEIVE' } })

    // 日時フィールドに値を入力
    const eventTimeInput = screen.getByLabelText(/作業日時/)
    fireEvent.change(eventTimeInput, { target: { value: '2026-01-01T10:00' } })

    // フォームを送信
    fireEvent.click(screen.getByRole('button', { name: '記録する' }))

    await waitFor(() => {
      // 成功後も追跡番号が保持されていること
      expect(screen.getByPlaceholderText('TRK-000001')).toHaveValue('TRK-000001')
    })
  })
})
