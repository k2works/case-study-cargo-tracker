import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { ShipperList } from './ShipperList'

vi.mock('../hooks/useShippers', () => ({
  useShippers: vi.fn(),
}))

import { useShippers } from '../hooks/useShippers'

const mockedUseShippers = vi.mocked(useShippers)

function renderShipperList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ShipperList />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('ShipperList', () => {
  it('読み込み中の表示がされる', () => {
    mockedUseShippers.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as unknown as ReturnType<typeof useShippers>)

    renderShipperList()
    expect(screen.getByText('読み込み中...')).toBeInTheDocument()
  })

  it('エラー時のメッセージが表示される', () => {
    mockedUseShippers.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as unknown as ReturnType<typeof useShippers>)

    renderShipperList()
    expect(screen.getByText('データの取得に失敗しました。')).toBeInTheDocument()
  })

  it('荷主が0件のときに空メッセージが表示される', () => {
    mockedUseShippers.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useShippers>)

    renderShipperList()
    expect(screen.getByText('荷主が登録されていません。')).toBeInTheDocument()
  })

  it('荷主一覧がテーブルで表示される', () => {
    mockedUseShippers.mockReturnValue({
      data: [
        {
          id: 1,
          shipperCode: 'SHP001',
          shipperType: 'INDIVIDUAL',
          name: '山田太郎',
          email: 'yamada@example.com',
          phone: '090-1234-5678',
          contractNumber: null,
          discountRate: null,
        },
        {
          id: 2,
          shipperCode: 'SHP002',
          shipperType: 'CORPORATE',
          name: '株式会社テスト',
          email: 'test@corp.com',
          phone: null,
          contractNumber: 'C-001',
          discountRate: 0.1,
        },
      ],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useShippers>)

    renderShipperList()
    expect(screen.getByText('SHP001')).toBeInTheDocument()
    expect(screen.getByText('山田太郎')).toBeInTheDocument()
    expect(screen.getByText('株式会社テスト')).toBeInTheDocument()
    expect(screen.getByText('個人')).toBeInTheDocument()
    expect(screen.getByText('法人')).toBeInTheDocument()
  })
})
