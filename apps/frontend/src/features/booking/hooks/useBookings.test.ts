import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useBookCargo, useBookings } from './useBookings'
import * as apiClient from '../../../lib/api-client'
import type { BookCargoRequest, BookingListItem } from '../types/booking'

vi.mock('../../../lib/api-client', () => ({
  bookingApiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

const baseRequest: BookCargoRequest = {
  shipperId: 1,
  cargoSpec: {
    cargoType: 'GENERAL',
    weightKg: 100,
    quantity: 1,
    productName: '産業機械',
    dimensions: { lengthCm: 100, widthCm: 50, heightCm: 30 },
  },
  routeSpec: {
    originUnLocode: 'JPYOK',
    destinationUnLocode: 'USLAX',
    arrivalDeadline: '2099-12-31',
  },
}

describe('useBookCargo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('予約を登録すると bookingId と bookingStatus が返る', async () => {
    const mockResponse = { bookingId: 'uuid-123', bookingStatus: 'PRELIMINARY' }
    vi.mocked(apiClient.bookingApiClient.post).mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useBookCargo(), { wrapper: createWrapper() })
    result.current.mutate(baseRequest)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockResponse)
    expect(apiClient.bookingApiClient.post).toHaveBeenCalledWith('/api/v1/bookings', baseRequest)
  })

  it('API エラー時に isError が true になる', async () => {
    vi.mocked(apiClient.bookingApiClient.post).mockRejectedValue(new Error('Network Error'))

    const { result } = renderHook(() => useBookCargo(), { wrapper: createWrapper() })
    result.current.mutate(baseRequest)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useBookings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('予約一覧を取得する', async () => {
    const mockBookings: BookingListItem[] = [
      {
        bookingId: 'b-1',
        shipperId: 1,
        trackingNumber: null,
        cargoType: 'GENERAL',
        productName: '産業機械',
        originUnLocode: 'JPYOK',
        destinationUnLocode: 'USLAX',
        arrivalDeadline: '2099-12-31',
        bookingStatus: 'PRELIMINARY',
        routingStatus: 'NOT_ROUTED',
        createdAt: '2026-05-15T10:00:00',
      },
    ]
    vi.mocked(apiClient.bookingApiClient.get).mockResolvedValue(mockBookings)

    const { result } = renderHook(() => useBookings(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockBookings)
    expect(apiClient.bookingApiClient.get).toHaveBeenCalledWith('/api/v1/bookings')
  })

  it('API エラー時に isError が true になる', async () => {
    vi.mocked(apiClient.bookingApiClient.get).mockRejectedValue(new Error('Network Error'))

    const { result } = renderHook(() => useBookings(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
