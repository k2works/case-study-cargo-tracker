import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { VoyageForm } from './VoyageForm'

vi.mock('../hooks/useVoyages', () => ({
  useRegisterVoyage: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  }),
}))

function renderVoyageForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <VoyageForm />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('VoyageForm', () => {
  it('航海番号・船名・運送会社・出発港・到着港・日時の入力欄が表示される', () => {
    renderVoyageForm()
    expect(screen.getByLabelText('航海番号')).toBeInTheDocument()
    expect(screen.getByLabelText('船名')).toBeInTheDocument()
    expect(screen.getByLabelText('運送会社コード')).toBeInTheDocument()
    expect(screen.getByLabelText('運送会社名')).toBeInTheDocument()
    expect(screen.getByLabelText('出発港（UN/LOCODE）')).toBeInTheDocument()
    expect(screen.getByLabelText('到着港（UN/LOCODE）')).toBeInTheDocument()
    expect(screen.getByLabelText('出発日時')).toBeInTheDocument()
    expect(screen.getByLabelText('到着日時')).toBeInTheDocument()
  })

  it('対応貨物種別の 3 種類のチェックボックスが表示される', () => {
    renderVoyageForm()
    expect(screen.getByLabelText('一般貨物')).toBeInTheDocument()
    expect(screen.getByLabelText('危険物')).toBeInTheDocument()
    expect(screen.getByLabelText('冷凍・冷蔵')).toBeInTheDocument()
  })

  it('初期状態では寄港地が 1 件表示される', () => {
    renderVoyageForm()
    expect(screen.getAllByLabelText(/^寄港地 \d+ 出発港/)).toHaveLength(1)
  })

  it('「寄港地を追加」ボタンで寄港地行を増やせる', async () => {
    const user = userEvent.setup()
    renderVoyageForm()

    await user.click(screen.getByRole('button', { name: '寄港地を追加' }))

    expect(screen.getAllByLabelText(/^寄港地 \d+ 出発港/)).toHaveLength(2)
  })

  it('登録するボタンとキャンセルボタンが表示される', () => {
    renderVoyageForm()
    expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeInTheDocument()
  })
})
