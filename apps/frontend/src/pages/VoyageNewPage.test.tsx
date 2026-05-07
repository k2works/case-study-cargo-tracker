import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { VoyageNewPage } from './VoyageNewPage'
import * as useVoyagesModule from '../features/routing/hooks/useVoyages'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <VoyageNewPage />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('VoyageNewPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
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
    expect(screen.getByText('航海新規登録')).toBeInTheDocument()
  })

  it('登録フォームが表示される', () => {
    renderPage()
    expect(screen.getByLabelText('航海番号')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '登録' })).toBeInTheDocument()
  })
})
