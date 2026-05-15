import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { VoyageEditForm } from './VoyageEditForm'
import type { VoyageListItem } from '../types/voyage'

const mutateMock = vi.fn()
let pendingState = false
let errorState = false

vi.mock('../hooks/useVoyages', () => ({
  useUpdateVoyage: () => ({
    mutate: mutateMock,
    get isPending() {
      return pendingState
    },
    get isError() {
      return errorState
    },
    error: null,
  }),
}))

const sampleVoyage: VoyageListItem = {
  voyageNumber: 'V-EDIT-001',
  carrierCode: 'MOL',
  carrierName: 'Mitsui O.S.K. Lines',
  shipName: 'Yokohama Express',
  originUnLocode: 'JPYOK',
  destinationUnLocode: 'USLAX',
  departureDate: '2026-07-01T09:00:00',
  arrivalDate: '2026-07-15T18:00:00',
  status: 'SCHEDULED',
}

function renderEditForm(voyage = sampleVoyage) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <VoyageEditForm current={voyage} />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('VoyageEditForm', () => {
  beforeEach(() => {
    mutateMock.mockReset()
    pendingState = false
    errorState = false
  })

  it('運送会社・船名・出発港・到着港は read-only で表示される', () => {
    renderEditForm()
    // 運送会社名・船名は単一フィールドのみ
    expect(screen.getByDisplayValue('Mitsui O.S.K. Lines')).toHaveAttribute('readonly')
    expect(screen.getByDisplayValue('Yokohama Express')).toHaveAttribute('readonly')
    // 出発港 / 到着港 UN/LOCODE は read-only と寄港地入力（編集可）の両方に現れるため、
    // どちらかが readonly 属性を持っていることだけ確認する
    const origins = screen.getAllByDisplayValue('JPYOK')
    expect(origins.some((el) => el.hasAttribute('readonly'))).toBe(true)
    const destinations = screen.getAllByDisplayValue('USLAX')
    expect(destinations.some((el) => el.hasAttribute('readonly'))).toBe(true)
  })

  it('出発日時を変更すると「✎ 変更」バッジが表示される', async () => {
    renderEditForm()
    expect(screen.queryByTestId('change-badge')).not.toBeInTheDocument()

    const departure = screen.getByTestId('departure-date') as HTMLInputElement
    await userEvent.clear(departure)
    await userEvent.type(departure, '2026-07-03T09:00')
    expect(screen.getByTestId('change-badge')).toBeInTheDocument()
  })

  it('「更新する」ボタンで useUpdateVoyage.mutate が呼ばれる', async () => {
    renderEditForm()
    await userEvent.click(screen.getByRole('button', { name: '更新する' }))
    expect(mutateMock).toHaveBeenCalledTimes(1)
    const [arg] = mutateMock.mock.calls[0]
    expect(arg).toHaveProperty('departureDate', '2026-07-01T09:00:00')
    expect(arg).toHaveProperty('arrivalDate', '2026-07-15T18:00:00')
    expect(Array.isArray(arg.carrierMovements)).toBe(true)
    expect(Array.isArray(arg.acceptedCargoTypes)).toBe(true)
  })

  it('「キャンセル」ボタンは mutate を呼ばずに一覧に戻る（受入条件 5）', async () => {
    renderEditForm()
    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(mutateMock).not.toHaveBeenCalled()
  })
})
