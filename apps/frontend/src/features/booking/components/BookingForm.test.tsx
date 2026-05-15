import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { BookingForm } from './BookingForm'

vi.mock('../hooks/useBookings', () => ({
  useBookCargo: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  }),
}))

function renderBookingForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <BookingForm />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('BookingForm', () => {
  it('荷主 ID と貨物仕様、経路仕様の入力欄が表示される', () => {
    renderBookingForm()
    expect(screen.getByLabelText('荷主 ID')).toBeInTheDocument()
    expect(screen.getByLabelText('貨物種別')).toBeInTheDocument()
    expect(screen.getByLabelText('重量（kg）')).toBeInTheDocument()
    expect(screen.getByLabelText('個数')).toBeInTheDocument()
    expect(screen.getByLabelText('品名')).toBeInTheDocument()
    expect(screen.getByLabelText('長さ（cm）')).toBeInTheDocument()
    expect(screen.getByLabelText('幅（cm）')).toBeInTheDocument()
    expect(screen.getByLabelText('高さ（cm）')).toBeInTheDocument()
    expect(screen.getByLabelText('出発地（UN/LOCODE）')).toBeInTheDocument()
    expect(screen.getByLabelText('目的地（UN/LOCODE）')).toBeInTheDocument()
    expect(screen.getByLabelText('到着期限')).toBeInTheDocument()
  })

  it('デフォルトでは貨物種別は GENERAL で追加フィールドは表示されない', () => {
    renderBookingForm()
    expect(screen.getByLabelText('貨物種別')).toHaveValue('GENERAL')
    expect(screen.queryByLabelText('IMO クラス')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('最低温度（℃）')).not.toBeInTheDocument()
  })

  it('貨物種別を HAZARDOUS に切り替えると HazardInfo の入力欄が表示される', async () => {
    const user = userEvent.setup()
    renderBookingForm()
    await user.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS')
    expect(screen.getByLabelText('IMO クラス')).toBeInTheDocument()
  })

  it('貨物種別を REFRIGERATED に切り替えると TemperatureCondition の入力欄が表示される', async () => {
    const user = userEvent.setup()
    renderBookingForm()
    await user.selectOptions(screen.getByLabelText('貨物種別'), 'REFRIGERATED')
    expect(screen.getByLabelText('最低温度（℃）')).toBeInTheDocument()
    expect(screen.getByLabelText('最高温度（℃）')).toBeInTheDocument()
  })

  it('登録するボタンとキャンセルボタンが表示される', () => {
    renderBookingForm()
    expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeInTheDocument()
  })
})
