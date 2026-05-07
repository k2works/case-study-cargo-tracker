import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { VoyageForm } from './VoyageForm'
import * as useVoyagesModule from '../hooks/useVoyages'

function renderVoyageForm(voyage?: Parameters<typeof VoyageForm>[0]['voyage']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <VoyageForm voyage={voyage} />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('VoyageForm', () => {
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

  it('新規登録フォームに航海番号入力欄が表示される', () => {
    renderVoyageForm()
    expect(screen.getByLabelText('航海番号')).toBeInTheDocument()
  })

  it('新規登録フォームに「登録」ボタンが表示される', () => {
    renderVoyageForm()
    expect(screen.getByRole('button', { name: '登録' })).toBeInTheDocument()
  })

  it('編集フォームには航海番号入力欄が表示されない', () => {
    renderVoyageForm({
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
    })
    expect(screen.queryByLabelText('航海番号')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '更新' })).toBeInTheDocument()
  })
})
