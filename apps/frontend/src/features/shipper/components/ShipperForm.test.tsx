import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import { ShipperForm } from './ShipperForm'

vi.mock('../hooks/useShippers', () => ({
  useRegisterShipper: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  }),
}))

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

  it('各入力欄に値を入力できる', async () => {
    const user = userEvent.setup()
    renderShipperForm()

    await user.type(screen.getByLabelText('氏名/社名'), '山田太郎')
    await user.type(screen.getByLabelText('メールアドレス'), 'test@example.com')
    await user.type(screen.getByLabelText('電話番号'), '090-1234-5678')

    expect(screen.getByLabelText('氏名/社名')).toHaveValue('山田太郎')
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('test@example.com')
    expect(screen.getByLabelText('電話番号')).toHaveValue('090-1234-5678')
  })

  it('法人選択時に契約番号と割引率を入力できる', async () => {
    const user = userEvent.setup()
    renderShipperForm()

    await user.click(screen.getByLabelText('法人'))
    await user.type(screen.getByLabelText('契約番号'), 'C-001')
    await user.type(screen.getByLabelText('割引率（0〜30%）'), '10')

    expect(screen.getByLabelText('契約番号')).toHaveValue('C-001')
    expect(screen.getByLabelText('割引率（0〜30%）')).toHaveValue(10)
  })

  it('フォーム送信時に登録するボタンが存在する', async () => {
    const user = userEvent.setup()
    renderShipperForm()

    await user.type(screen.getByLabelText('氏名/社名'), '山田太郎')
    await user.type(screen.getByLabelText('メールアドレス'), 'test@example.com')
    await user.click(screen.getByRole('button', { name: '登録する' }))

    expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument()
  })
})
