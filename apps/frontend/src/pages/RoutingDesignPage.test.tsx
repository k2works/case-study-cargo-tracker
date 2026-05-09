import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RoutingDesignPage } from './RoutingDesignPage'
import * as useItinerariesModule from '../features/routing/hooks/useItineraries'

function renderPage(bookingId = 'BK001') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/routing/design/${bookingId}`]}>
        <Routes>
          <Route path="/routing/design/:bookingId" element={<RoutingDesignPage />} />
          <Route path="/routing/assignments" element={<div>経路設計担当一覧</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RoutingDesignPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useItinerariesModule, 'useItineraries').mockReturnValue({
      mutate: vi.fn(),
      data: undefined,
      isPending: false,
      isError: false,
    } as unknown as ReturnType<typeof useItinerariesModule.useItineraries>)
  })

  it('ページタイトルが表示される', () => {
    renderPage()
    expect(screen.getByText(/経路設計/)).toBeInTheDocument()
  })

  it('検索フォームが表示される', () => {
    renderPage()
    expect(screen.getByRole('button', { name: '経路を検索' })).toBeInTheDocument()
  })

  it('フォームにデフォルト値（JPTYO/CNSHA）が入力されている', () => {
    renderPage()
    expect(screen.getByDisplayValue('JPTYO')).toBeInTheDocument()
    expect(screen.getByDisplayValue('CNSHA')).toBeInTheDocument()
  })

  it('ページ表示時にデフォルト値で自動検索が実行される', () => {
    const mockMutate = vi.fn()
    vi.spyOn(useItinerariesModule, 'useItineraries').mockReturnValue({
      mutate: mockMutate,
      data: undefined,
      isPending: false,
      isError: false,
    } as unknown as ReturnType<typeof useItinerariesModule.useItineraries>)

    renderPage()
    expect(mockMutate).toHaveBeenCalledWith({
      originUnlocode: 'JPTYO',
      destinationUnlocode: 'CNSHA',
      arrivalDeadline: null,
    })
  })

  it('bookingId なしでアクセスすると経路設計担当一覧にリダイレクトされる', () => {
    const queryClient = new QueryClient()
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/routing/design']}>
          <Routes>
            <Route path="/routing/design" element={<RoutingDesignPage />} />
            <Route path="/routing/assignments" element={<div>経路設計担当一覧</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(screen.getByText('経路設計担当一覧')).toBeInTheDocument()
  })

  it('経路が存在しない場合メッセージが表示される', () => {
    vi.spyOn(useItinerariesModule, 'useItineraries').mockReturnValue({
      mutate: vi.fn(),
      data: [],
      isPending: false,
      isError: false,
    } as unknown as ReturnType<typeof useItinerariesModule.useItineraries>)

    renderPage()
    expect(screen.getByText('該当する経路が見つかりませんでした。')).toBeInTheDocument()
  })
})
