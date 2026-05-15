import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { VoyageForm } from './VoyageForm'

const mutateMock = vi.fn()
let pendingState = false
let errorState = false

vi.mock('../hooks/useVoyages', () => ({
  useRegisterVoyage: () => ({
    mutate: mutateMock,
    get isPending() {
      return pendingState
    },
    get isError() {
      return errorState
    },
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

  it('寄港地を追加後に削除ボタンで取り除ける（1 件以下にはできない）', async () => {
    const user = userEvent.setup()
    renderVoyageForm()

    await user.click(screen.getByRole('button', { name: '寄港地を追加' }))
    expect(screen.getAllByLabelText(/^寄港地 \d+ 出発港/)).toHaveLength(2)

    const deleteButtons = screen.getAllByRole('button', { name: '削除' })
    await user.click(deleteButtons[1])
    expect(screen.getAllByLabelText(/^寄港地 \d+ 出発港/)).toHaveLength(1)
  })

  it('対応貨物種別のチェックで追加/解除ができる', async () => {
    const user = userEvent.setup()
    renderVoyageForm()

    const hazardous = screen.getByLabelText('危険物') as HTMLInputElement
    expect(hazardous.checked).toBe(false)
    await user.click(hazardous)
    expect(hazardous.checked).toBe(true)
    await user.click(hazardous)
    expect(hazardous.checked).toBe(false)
  })

  it('フォーム送信で mutate が呼ばれ、_id は API ペイロードに含まれない', async () => {
    mutateMock.mockClear()
    pendingState = false
    errorState = false
    const user = userEvent.setup()
    renderVoyageForm()

    await user.type(document.getElementById('voyage-number') as HTMLInputElement, 'V-TEST-01')
    await user.type(document.getElementById('voyage-ship-name') as HTMLInputElement, 'Ship')
    await user.type(document.getElementById('voyage-carrier-code') as HTMLInputElement, 'MOL')
    await user.type(document.getElementById('voyage-carrier-name') as HTMLInputElement, 'Mitsui')
    await user.type(document.getElementById('voyage-origin') as HTMLInputElement, 'JPYOK')
    await user.type(document.getElementById('voyage-destination') as HTMLInputElement, 'USLAX')
    await user.type(document.getElementById('voyage-departure') as HTMLInputElement, '2099-07-01T09:00')
    await user.type(document.getElementById('voyage-arrival') as HTMLInputElement, '2099-07-15T18:00')
    await user.type(document.getElementById('movement-0-from') as HTMLInputElement, 'JPYOK')
    await user.type(document.getElementById('movement-0-to') as HTMLInputElement, 'USLAX')
    await user.type(document.getElementById('movement-0-dep-time') as HTMLInputElement, '2099-07-01T09:00')
    await user.type(document.getElementById('movement-0-arr-time') as HTMLInputElement, '2099-07-15T18:00')

    await user.click(screen.getByRole('button', { name: '登録する' }))

    expect(mutateMock).toHaveBeenCalledTimes(1)
    const [request] = mutateMock.mock.calls[0]
    expect(request.voyageNumber).toBe('V-TEST-01')
    expect(request.carrierMovements).toHaveLength(1)
    // 内部 UI 用 _id は API へ漏れない
    expect(request.carrierMovements[0]).not.toHaveProperty('_id')
    expect(request.acceptedCargoTypes).toEqual(['GENERAL'])
  })

  it('isPending のとき登録ボタンが「登録中...」になり disabled になる', () => {
    pendingState = true
    errorState = false
    renderVoyageForm()
    expect(screen.getByRole('button', { name: '登録中...' })).toBeDisabled()
    pendingState = false
  })

  it('isError のときエラーメッセージが表示される', () => {
    pendingState = false
    errorState = true
    renderVoyageForm()
    expect(screen.getByText('登録に失敗しました。')).toBeInTheDocument()
    errorState = false
  })
})
