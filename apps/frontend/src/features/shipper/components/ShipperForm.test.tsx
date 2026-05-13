import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect } from 'vitest'
import { ShipperForm } from './ShipperForm'

function renderShipperForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ShipperForm />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('ShipperForm', () => {
  it('氏名/社名、メール、電話番号の入力欄が表示される', () => {
    renderShipperForm()
    expect(screen.getByLabelText('氏名/社名')).toBeInTheDocument()
    expect(screen.getByLabelText('メールアドレス')).toBeInTheDocument()
    expect(screen.getByLabelText('電話番号')).toBeInTheDocument()
  })

  it('荷主種別の個人・法人ラジオボタンが表示される', () => {
    renderShipperForm()
    expect(screen.getByLabelText('個人')).toBeInTheDocument()
    expect(screen.getByLabelText('法人')).toBeInTheDocument()
  })

  it('デフォルトでは個人が選択されている', () => {
    renderShipperForm()
    expect(screen.getByLabelText('個人')).toBeChecked()
    expect(screen.getByLabelText('法人')).not.toBeChecked()
  })

  it('法人を選択すると契約番号と割引率の入力欄が表示される', async () => {
    const user = userEvent.setup()
    renderShipperForm()

    expect(screen.queryByLabelText('契約番号')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('割引率（0〜30%）')).not.toBeInTheDocument()

    await user.click(screen.getByLabelText('法人'))

    expect(screen.getByLabelText('契約番号')).toBeInTheDocument()
    expect(screen.getByLabelText('割引率（0〜30%）')).toBeInTheDocument()
  })

  it('登録するボタンとキャンセルボタンが表示される', () => {
    renderShipperForm()
    expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeInTheDocument()
  })
})
