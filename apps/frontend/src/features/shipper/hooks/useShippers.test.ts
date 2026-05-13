import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useShippers, useRegisterShipper } from './useShippers'
import * as apiClient from '../../../lib/api-client'

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

describe('useShippers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('荷主一覧を取得する', async () => {
    const mockShippers = [{ id: 1, shipperCode: 'SHP001', name: '山田太郎' }]
    vi.mocked(apiClient.bookingApiClient.get).mockResolvedValue(mockShippers)

    const { result } = renderHook(() => useShippers(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockShippers)
  })

  it('API エラー時に isError が true になる', async () => {
    vi.mocked(apiClient.bookingApiClient.get).mockRejectedValue(new Error('Network Error'))

    const { result } = renderHook(() => useShippers(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useRegisterShipper', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('荷主を登録する', async () => {
    const mockShipper = { id: 1, shipperCode: 'SHP001', name: '山田太郎' }
    vi.mocked(apiClient.bookingApiClient.post).mockResolvedValue(mockShipper)
    vi.mocked(apiClient.bookingApiClient.get).mockResolvedValue([])

    const { result } = renderHook(() => useRegisterShipper(), { wrapper: createWrapper() })

    result.current.mutate({ name: '山田太郎', email: 'test@example.com', phone: '090-1234-5678', shipperType: 'INDIVIDUAL' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockShipper)
  })
})
