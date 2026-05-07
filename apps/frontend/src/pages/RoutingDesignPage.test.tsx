import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RoutingDesignPage } from './RoutingDesignPage'
import * as useItinerariesModule from '../features/routing/hooks/useItineraries'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <RoutingDesignPage />
      </BrowserRouter>
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
    expect(screen.getByText('経路設計')).toBeInTheDocument()
  })

  it('検索フォームが表示される', () => {
    renderPage()
    expect(screen.getByRole('button', { name: '経路を検索' })).toBeInTheDocument()
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
