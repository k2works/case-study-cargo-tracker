import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { VoyageListPage } from './VoyageListPage'
import * as useVoyagesModule from '../features/routing/hooks/useVoyages'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <VoyageListPage />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('VoyageListPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(useVoyagesModule, 'useVoyages').mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useVoyagesModule.useVoyages>)
    vi.spyOn(useVoyagesModule, 'useDeleteVoyage').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useDeleteVoyage>)
    vi.spyOn(useVoyagesModule, 'useCreateVoyage').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useCreateVoyage>)
    vi.spyOn(useVoyagesModule, 'useUpdateVoyage').mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useVoyagesModule.useUpdateVoyage>)
  })

  it('ページタイトルが表示される', () => {
    renderPage()
    expect(screen.getByText('航海スケジュール管理')).toBeInTheDocument()
  })

  it('新規登録リンクが表示される', () => {
    renderPage()
    expect(screen.getByRole('link', { name: '新規登録' })).toBeInTheDocument()
  })

  it('航海がない場合メッセージが表示される', () => {
    renderPage()
    expect(screen.getByText('航海が登録されていません。')).toBeInTheDocument()
  })
})
