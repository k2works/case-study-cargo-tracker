import { render, screen, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { VoyageList } from './VoyageList'
import type { VoyageListItem } from '../types/voyage'

const mocks = vi.hoisted(() => ({
  useVoyagesResult: {
    data: undefined as VoyageListItem[] | undefined,
    isLoading: false,
    isError: false,
  },
  lastCriteria: undefined as Record<string, string> | undefined,
}))

vi.mock('../hooks/useVoyages', () => ({
  useVoyages: (criteria?: Record<string, string>) => {
    mocks.lastCriteria = criteria
    return mocks.useVoyagesResult
  },
}))

function renderVoyageList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <VoyageList />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('VoyageList', () => {
  it('読み込み中はメッセージを表示する', () => {
    mocks.useVoyagesResult = { data: undefined, isLoading: true, isError: false }
    renderVoyageList()
    expect(screen.getByText('読み込み中...')).toBeInTheDocument()
  })

  it('エラー時はエラーメッセージを表示する', () => {
    mocks.useVoyagesResult = { data: undefined, isLoading: false, isError: true }
    renderVoyageList()
    expect(screen.getByText('データの取得に失敗しました。')).toBeInTheDocument()
  })

  it('データが空のときは案内文を表示する', () => {
    mocks.useVoyagesResult = { data: [], isLoading: false, isError: false }
    renderVoyageList()
    expect(screen.getByText('航海スケジュールが登録されていません。')).toBeInTheDocument()
  })

  it('US07: 検索フォームが表示される', () => {
    mocks.useVoyagesResult = { data: [], isLoading: false, isError: false }
    renderVoyageList()
    expect(screen.getByPlaceholderText('出発港 (JPYOK)')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('到着港 (USLAX)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '検索' })).toBeInTheDocument()
  })

  it('US07: 出発地・目的地を入力して検索すると criteria が渡される', () => {
    mocks.useVoyagesResult = { data: [], isLoading: false, isError: false }
    mocks.lastCriteria = undefined
    renderVoyageList()
    fireEvent.change(screen.getByPlaceholderText('出発港 (JPYOK)'), { target: { value: 'JPYOK' } })
    fireEvent.change(screen.getByPlaceholderText('到着港 (USLAX)'), { target: { value: 'USLAX' } })
    fireEvent.click(screen.getByRole('button', { name: '検索' }))
    expect(mocks.lastCriteria).toMatchObject({ origin: 'JPYOK', destination: 'USLAX' })
  })

  it('航海一覧をテーブル形式で表示する', () => {
    mocks.useVoyagesResult = {
      data: [
        {
          voyageNumber: 'V12345',
          carrierCode: 'MOL',
          carrierName: 'Mitsui O.S.K. Lines',
          shipName: 'Yokohama Express',
          originUnLocode: 'JPYOK',
          destinationUnLocode: 'USLAX',
          departureDate: '2026-07-01T09:00:00',
          arrivalDate: '2026-07-15T18:00:00',
          status: 'SCHEDULED',
        },
      ],
      isLoading: false,
      isError: false,
    }
    renderVoyageList()
    expect(screen.getByText('V12345')).toBeInTheDocument()
    expect(screen.getByText('Yokohama Express')).toBeInTheDocument()
    expect(screen.getByText('JPYOK')).toBeInTheDocument()
    expect(screen.getByText('USLAX')).toBeInTheDocument()
    expect(screen.getByText('SCHEDULED')).toBeInTheDocument()
  })
})
