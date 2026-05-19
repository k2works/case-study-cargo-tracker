import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrackingExceptionForm } from './TrackingExceptionForm'

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

const mockOnSuccess = vi.fn()
const mockMutate = vi.fn()

vi.mock('../hooks/useTracking', () => ({
  useRegisterTrackingException: () => ({
    mutate: mockMutate,
    isPending: false,
    isError: false,
    error: null,
  }),
}))

describe('TrackingExceptionForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('フォームが表示される', () => {
    render(<TrackingExceptionForm trackingNumber="TRK-001" onSuccess={mockOnSuccess} />, {
      wrapper,
    })
    expect(screen.getByRole('combobox', { name: /例外種別/ })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /説明/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /登録/ })).toBeInTheDocument()
  })

  it('必須項目が未入力の場合は送信されない', async () => {
    render(<TrackingExceptionForm trackingNumber="TRK-001" onSuccess={mockOnSuccess} />, {
      wrapper,
    })
    fireEvent.click(screen.getByRole('button', { name: /登録/ }))
    await waitFor(() => {
      expect(mockMutate).not.toHaveBeenCalled()
    })
  })

  it('フォーム送信で mutate が呼ばれる', async () => {
    render(<TrackingExceptionForm trackingNumber="TRK-001" onSuccess={mockOnSuccess} />, {
      wrapper,
    })

    fireEvent.change(screen.getByRole('combobox', { name: /例外種別/ }), {
      target: { value: 'DELAY' },
    })
    fireEvent.change(screen.getByRole('textbox', { name: /説明/ }), {
      target: { value: '貨物が遅延しています' },
    })
    fireEvent.click(screen.getByRole('button', { name: /登録/ }))

    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith(
        expect.objectContaining({
          trackingNumber: 'TRK-001',
          exceptionType: 'DELAY',
          description: '貨物が遅延しています',
        }),
        expect.any(Object),
      )
    })
  })
})
