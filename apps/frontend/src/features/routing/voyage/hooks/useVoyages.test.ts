import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useRegisterVoyage, useVoyages } from './useVoyages'
import * as apiClient from '../../../../lib/api-client'
import type { RegisterVoyageRequest, VoyageListItem } from '../types/voyage'

vi.mock('../../../../lib/api-client', () => ({
  routingApiClient: {
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

const baseRequest: RegisterVoyageRequest = {
  voyageNumber: 'V12345',
  carrierCode: 'MOL',
  carrierName: 'Mitsui O.S.K. Lines',
  shipName: 'Yokohama Express',
  originUnLocode: 'JPYOK',
  destinationUnLocode: 'USLAX',
  departureDate: '2026-07-01T09:00:00',
  arrivalDate: '2026-07-15T18:00:00',
  carrierMovements: [
    {
      departureUnLocode: 'JPYOK',
      arrivalUnLocode: 'USLAX',
      departureTime: '2026-07-01T09:00:00',
      arrivalTime: '2026-07-15T18:00:00',
    },
  ],
  acceptedCargoTypes: ['GENERAL'],
}

describe('useRegisterVoyage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('航海スケジュールを登録すると voyageNumber と status が返る', async () => {
    const mockResponse = { voyageNumber: 'V12345', status: 'SCHEDULED' }
    vi.mocked(apiClient.routingApiClient.post).mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useRegisterVoyage(), { wrapper: createWrapper() })
    result.current.mutate(baseRequest)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockResponse)
    expect(apiClient.routingApiClient.post).toHaveBeenCalledWith('/api/v1/voyages', baseRequest)
  })

  it('API エラー時に isError が true になる', async () => {
    vi.mocked(apiClient.routingApiClient.post).mockRejectedValue(new Error('Network Error'))

    const { result } = renderHook(() => useRegisterVoyage(), { wrapper: createWrapper() })
    result.current.mutate(baseRequest)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useVoyages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('航海スケジュール一覧を取得する', async () => {
    const mockVoyages: VoyageListItem[] = [
      {
        voyageNumber: 'V12345',
        carrierCode: 'MOL',
        carrierName: 'Mitsui O.S.K. Lines',
        shipName: 'Yokohama Express',
        originUnLocode: 'JPYOK',
        destinationUnLocode: 'USLAX',
        departureDate: '2026-07-01T09:00:00',
        arrivalDate: '2026-07-15T18:00:00',
        status: 'SCHEDULED',
      },
    ]
    vi.mocked(apiClient.routingApiClient.get).mockResolvedValue(mockVoyages)

    const { result } = renderHook(() => useVoyages(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockVoyages)
    expect(apiClient.routingApiClient.get).toHaveBeenCalledWith('/api/v1/voyages')
  })

  it('US07: 出発地・目的地を指定すると検索クエリパラメータ付きで取得する', async () => {
    vi.mocked(apiClient.routingApiClient.get).mockResolvedValue([])

    const { result } = renderHook(
      () => useVoyages({ origin: 'JPYOK', destination: 'USLAX' }),
      { wrapper: createWrapper() },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.routingApiClient.get).toHaveBeenCalledWith(
      '/api/v1/voyages?origin=JPYOK&destination=USLAX',
    )
  })

  it('US07: 貨物種別を指定すると cargoType クエリパラメータ付きで取得する', async () => {
    vi.mocked(apiClient.routingApiClient.get).mockResolvedValue([])

    const { result } = renderHook(
      () => useVoyages({ cargoType: 'HAZARDOUS' }),
      { wrapper: createWrapper() },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.routingApiClient.get).toHaveBeenCalledWith(
      '/api/v1/voyages?cargoType=HAZARDOUS',
    )
  })
})
