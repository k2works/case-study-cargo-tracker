import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { VoyageList } from './VoyageList'
import * as useVoyagesModule from '../hooks/useVoyages'
import type { Voyage } from '../types/voyage'

function renderVoyageList(onEdit?: (v: Voyage) => void) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <VoyageList onEdit={onEdit} />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

const mockVoyage: Voyage = {
  id: 1,
  voyageNumber: 'V001',
  carrierMovements: [
    {
      departureLocationUnlocode: 'JPTYO',
      arrivalLocationUnlocode: 'CNSHA',
      departureDate: '2025-01-10T08:00:00+09:00',
      arrivalDate: '2025-01-12T18:00:00+09:00',
      seqNumber: 0,
    },
  ],
}

describe('VoyageList', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('読み込み中メッセージが表示される', () => {
    vi.spyOn(useVoyagesModule, 'useVoyages').mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useVoyages>)
    vi.spyOn(useVoyagesModule, 'useDeleteVoyage').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useDeleteVoyage>)

    renderVoyageList()
    expect(screen.getByText('読み込み中...')).toBeInTheDocument()
  })

  it('航海一覧が表示される', () => {
    vi.spyOn(useVoyagesModule, 'useVoyages').mockReturnValue({
      data: [mockVoyage],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useVoyages>)
    vi.spyOn(useVoyagesModule, 'useDeleteVoyage').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useDeleteVoyage>)

    renderVoyageList()
    expect(screen.getByText('V001')).toBeInTheDocument()
  })

  it('航海がない場合メッセージが表示される', () => {
    vi.spyOn(useVoyagesModule, 'useVoyages').mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useVoyages>)
    vi.spyOn(useVoyagesModule, 'useDeleteVoyage').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useDeleteVoyage>)

    renderVoyageList()
    expect(screen.getByText('航海が登録されていません。')).toBeInTheDocument()
  })
})
