import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BookingForm } from './BookingForm'
import * as useBookingsModule from '../hooks/useBookings'

function renderForm(props = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BookingForm {...props} />
    </QueryClientProvider>,
  )
}

describe('BookingForm', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useBookingsModule, 'useCreateBooking').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useBookingsModule.useCreateBooking>)
  })

  it('フォームが表示される', () => {
    renderForm()
    expect(screen.getByLabelText('荷主 ID')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '登録' })).toBeInTheDocument()
  })

  it('フォームを送信すると createMutation.mutate が呼ばれる', () => {
    const mockMutate = vi.fn()
    vi.spyOn(useBookingsModule, 'useCreateBooking').mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useBookingsModule.useCreateBooking>)

    renderForm()
    fireEvent.submit(screen.getByRole('button', { name: '登録' }))
    expect(mockMutate).toHaveBeenCalled()
  })

  it('isPending 中は登録ボタンが無効化される', () => {
    vi.spyOn(useBookingsModule, 'useCreateBooking').mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
    } as unknown as ReturnType<typeof useBookingsModule.useCreateBooking>)

    renderForm()
    expect(screen.getByRole('button', { name: '登録' })).toBeDisabled()
  })

  it('キャンセルボタンをクリックすると onCancel が呼ばれる', () => {
    const onCancel = vi.fn()
    renderForm({ onCancel })
    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(onCancel).toHaveBeenCalled()
  })
})
