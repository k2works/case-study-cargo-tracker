import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { CargoStatusUpdateForm } from './CargoStatusUpdateForm'
import type { CargoSnapshotResponse, CargoStatusHistoryRecord } from '../types/handling'

const updateMutateMock = vi.fn()
let snapshotData: CargoSnapshotResponse | undefined
let snapshotLoading = false
let snapshotError = false
let historyData: CargoStatusHistoryRecord[] = []
let updatePending = false
let updateSuccess = false

vi.mock('../hooks/useHandling', () => ({
  useCargoSnapshot: () => ({
    data: snapshotData,
    isLoading: snapshotLoading,
    isError: snapshotError,
  }),
  useStatusHistory: () => ({
    data: historyData,
  }),
  useUpdateCargoStatus: () => ({
    mutate: updateMutateMock,
    get isPending() {
      return updatePending
    },
    get isSuccess() {
      return updateSuccess
    },
  }),
}))

function renderForm(trackingNumber: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/tracking/${trackingNumber}/manage`]}>
        <Routes>
          <Route path="/tracking/:trackingNumber/manage" element={<CargoStatusUpdateForm />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('CargoStatusUpdateForm (US17)', () => {
  beforeEach(() => {
    updateMutateMock.mockReset()
    snapshotLoading = false
    snapshotError = false
    snapshotData = {
      bookingId: 'B-2026-0001',
      trackingNumber: 'TRK-20260725-UPDATE01',
      originUnlocode: 'JPTYO',
      destinationUnlocode: 'DEHAM',
      cargoType: 'GENERAL',
    }
    historyData = []
    updatePending = false
    updateSuccess = false
  })

  it('US17 受入1: 現在の貨物情報（snapshot）が表示される', async () => {
    renderForm('TRK-20260725-UPDATE01')
    await waitFor(() => {
      expect(screen.getByTestId('snapshot-tracking')).toHaveTextContent('TRK-20260725-UPDATE01')
    })
    expect(screen.getByText('JPTYO → DEHAM')).toBeInTheDocument()
  })

  it('US17 受入1: 追跡番号が見つからない場合エラーメッセージが表示される', async () => {
    snapshotData = undefined
    snapshotError = true
    renderForm('TRK-NOTFOUND')
    expect(screen.getByTestId('snapshot-error')).toBeInTheDocument()
  })

  it('US17 受入2: 状態セレクトに IN_TRANSIT / DELIVERED / EXCEPTION の 3 つが表示される', () => {
    renderForm('TRK-20260725-UPDATE01')
    const select = screen.getByTestId('status-new-select') as HTMLSelectElement
    const options = Array.from(select.options).map((o) => o.value)
    expect(options).toEqual(['IN_TRANSIT', 'DELIVERED', 'EXCEPTION'])
  })

  it('US17 受入2: 送信時に mutate へ整形済みリクエストが渡される（UN/LOCODE は大文字化）', async () => {
    renderForm('TRK-20260725-UPDATE01')
    const user = userEvent.setup()
    await user.selectOptions(screen.getByTestId('status-new-select'), 'IN_TRANSIT')
    await user.type(screen.getByTestId('status-unlocode-input'), 'sgsin')
    await user.type(screen.getByTestId('status-updated-at-input'), '2026-07-25T08:00')
    await user.type(screen.getByTestId('status-operator-input'), 'tracker-001')
    await user.click(screen.getByTestId('status-submit'))

    expect(updateMutateMock).toHaveBeenCalledTimes(1)
    const request = updateMutateMock.mock.calls[0][0]
    expect(request).toEqual({
      newStatus: 'IN_TRANSIT',
      unlocode: 'SGSIN',
      updatedAt: '2026-07-25T08:00',
      operatorId: 'tracker-001',
    })
  })

  it('US17 受入3: 履歴があるとテーブルに表示される', () => {
    historyData = [
      {
        historyId: 'H-001',
        trackingNumber: 'TRK-20260725-UPDATE01',
        newStatus: 'IN_TRANSIT',
        unlocode: 'SGSIN',
        updatedAt: '2026-07-25T08:00:00',
        operatorId: 'tracker-001',
        recordedAt: '2026-07-25T08:00:00',
      },
    ]
    renderForm('TRK-20260725-UPDATE01')
    expect(screen.getByText('輸送中')).toBeInTheDocument()
    expect(screen.getByText('SGSIN')).toBeInTheDocument()
    expect(screen.getByText('tracker-001')).toBeInTheDocument()
  })
})
