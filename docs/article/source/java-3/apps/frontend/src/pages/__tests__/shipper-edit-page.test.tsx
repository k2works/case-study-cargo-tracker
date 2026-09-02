import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { ShipperEditPage } from '../shipper-edit-page'

const CORPORATE = {
  id: 2,
  shipperCode: 'SHP-000002',
  type: 'CORPORATE',
  name: '丸紅商事株式会社',
  email: 'corp@example.com',
  address: '東京都千代田区 1-1-1',
  phone: null,
  contractNumber: 'CN-2026-0001',
  discountRatePercent: 12.5,
}

function renderPage(id = 2) {
  return renderWithProviders(<ShipperEditPage />, [`/booking/shippers/${id}/edit`], undefined, {
    path: '/booking/shippers/:id/edit',
  })
}

describe('荷主の編集（US02 / #550）', () => {
  beforeEach(() => {
    loginAs(['ROLE_SALES'], '営業担当')
    server.use(http.get(`${API_PATHS.shippers}/:id`, () => HttpResponse.json(CORPORATE)))
  })

  it('登録済みの内容が初期値として入る', async () => {
    renderPage()

    expect(await screen.findByLabelText('氏名/社名')).toHaveValue('丸紅商事株式会社')
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('corp@example.com')
    expect(screen.getByLabelText('契約番号')).toHaveValue('CN-2026-0001')
    // 空欄は 0% ではなく「未設定」。12.5 がそのまま戻ること
    expect(screen.getByLabelText('割引率（%）')).toHaveValue(12.5)
  })

  it('荷主コードは変わらないことを画面に示す', async () => {
    renderPage()

    expect(await screen.findByText('SHP-000002')).toBeInTheDocument()
  })

  it('種別は変えられない', async () => {
    renderPage()

    expect(await screen.findByLabelText('荷主種別')).toBeDisabled()
  })

  it('直した内容を送ると一覧へ戻る', async () => {
    let sent: unknown = null
    server.use(
      http.put(`${API_PATHS.shippers}/:id`, async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(CORPORATE)
      }),
    )
    renderPage()

    const address = await screen.findByLabelText('住所')
    await userEvent.clear(address)
    await userEvent.type(address, '神奈川県横浜市 2-2-2')
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(sent).not.toBeNull())
    expect(sent).toMatchObject({ address: '神奈川県横浜市 2-2-2', type: 'CORPORATE' })
  })

  it('サーバが理由を添えて拒んだら、その理由を画面に残す', async () => {
    server.use(
      http.put(`${API_PATHS.shippers}/:id`, () =>
        HttpResponse.json({ message: '法人荷主には契約番号が必要です' }, { status: 400 }),
      ),
    )
    renderPage()

    await screen.findByLabelText('氏名/社名')
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('法人荷主には契約番号が必要です')
  })

  it('居ない荷主を開いたら、その旨を示して一覧へ戻れる', async () => {
    server.use(
      http.get(`${API_PATHS.shippers}/:id`, () =>
        HttpResponse.json({ message: '指定された荷主が見つかりません' }, { status: 404 }),
      ),
    )
    renderPage(999)

    expect(await screen.findByRole('alert')).toHaveTextContent('指定された荷主が見つかりません')
    expect(screen.getByRole('link', { name: '荷主一覧へ戻る' })).toBeInTheDocument()
  })
})
