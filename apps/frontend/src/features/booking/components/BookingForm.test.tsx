import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { BookingForm } from './BookingForm'

const mutateMock = vi.fn()
let pendingState = false
let errorState = false

vi.mock('../hooks/useBookings', () => ({
  useBookCargo: () => ({
    mutate: mutateMock,
    get isPending() {
      return pendingState
    },
    get isError() {
      return errorState
    },
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

  it('GENERAL 貨物のフォームを送信すると mutate がリクエストペイロード付きで呼ばれる', async () => {
    mutateMock.mockClear()
    pendingState = false
    errorState = false
    const user = userEvent.setup()
    renderBookingForm()

    // ラベルの丸括弧（全角/半角）に依存しないよう id 経由で取得する
    await user.type(document.getElementById('booking-shipper-id') as HTMLInputElement, '1')
    await user.type(document.getElementById('booking-weight') as HTMLInputElement, '100')
    const qty = document.getElementById('booking-quantity') as HTMLInputElement
    await user.clear(qty)
    await user.type(qty, '1')
    await user.type(document.getElementById('booking-product-name') as HTMLInputElement, '機械')
    await user.type(document.getElementById('booking-length') as HTMLInputElement, '10')
    await user.type(document.getElementById('booking-width') as HTMLInputElement, '20')
    await user.type(document.getElementById('booking-height') as HTMLInputElement, '30')
    await user.type(document.getElementById('booking-origin') as HTMLInputElement, 'JPYOK')
    await user.type(document.getElementById('booking-destination') as HTMLInputElement, 'USLAX')
    await user.type(document.getElementById('booking-deadline') as HTMLInputElement, '2099-12-31')

    await user.click(screen.getByRole('button', { name: '登録する' }))

    expect(mutateMock).toHaveBeenCalledTimes(1)
    const [request] = mutateMock.mock.calls[0]
    expect(request).toMatchObject({
      shipperId: 1,
      cargoSpec: { cargoType: 'GENERAL', weightKg: 100, quantity: 1, productName: '機械' },
      routeSpec: { originUnLocode: 'JPYOK', destinationUnLocode: 'USLAX' },
    })
  })

  it('isPending のとき登録ボタンが「登録中...」になり disabled になる', () => {
    pendingState = true
    errorState = false
    renderBookingForm()
    const button = screen.getByRole('button', { name: '登録中...' })
    expect(button).toBeDisabled()
    pendingState = false
  })

  it('isError のときエラーメッセージが表示される', () => {
    pendingState = false
    errorState = true
    renderBookingForm()
    expect(screen.getByText('登録に失敗しました。')).toBeInTheDocument()
    errorState = false
  })
})
