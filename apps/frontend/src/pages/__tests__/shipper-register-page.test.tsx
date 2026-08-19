import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { ShipperRegisterPage } from '../shipper-register-page'

const EXISTING = {
  id: 1,
  shipperCode: 'SHP-000001',
  type: 'INDIVIDUAL',
  name: '山田太郎',
  email: 'yamada@example.com',
  address: '東京都千代田区 1-1-1',
  phone: '03-1234-5678',
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/booking/shippers/new']}>
        <Routes>
          <Route path="/booking/shippers/new" element={<ShipperRegisterPage />} />
          <Route path="/booking/shippers" element={<h1>荷主一覧</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function fillForm() {
  await userEvent.type(screen.getByLabelText('氏名/社名'), '山田太郎')
  await userEvent.type(screen.getByLabelText('メールアドレス'), 'yamada@example.com')
  await userEvent.type(screen.getByLabelText('住所'), '東京都千代田区 1-1-1')
}

describe('荷主登録', () => {
  beforeEach(() => {
    server.use(
      http.post(API_PATHS.shippers, () => HttpResponse.json({ ...EXISTING }, { status: 201 })),
    )
  })

  it('個人荷主を登録すると荷主コードが分かる', async () => {
    renderPage()
    await fillForm()

    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText(/SHP-000001/)).toBeInTheDocument()
  })

  it('種別を法人に変えて登録できる', async () => {
    let sentType: string | undefined
    server.use(
      http.post(API_PATHS.shippers, async ({ request }) => {
        sentType = ((await request.json()) as { type: string }).type
        return HttpResponse.json({ ...EXISTING, type: 'CORPORATE' }, { status: 201 })
      }),
    )

    renderPage()
    await fillForm()
    await userEvent.selectOptions(screen.getByLabelText('荷主種別'), 'CORPORATE')
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    await screen.findByText(/SHP-000001/)
    expect(sentType).toBe('CORPORATE')
  })

  describe('同じメールアドレスの荷主が既にある場合', () => {
    beforeEach(() => {
      server.use(
        http.post(API_PATHS.shippers, async ({ request }) => {
          const body = (await request.json()) as { registerAnyway: boolean }
          if (body.registerAnyway) {
            return HttpResponse.json({ ...EXISTING, shipperCode: 'SHP-000002' }, { status: 201 })
          }
          return HttpResponse.json(
            { message: '同じメールアドレスの荷主が既に登録されています', existing: EXISTING },
            { status: 409 },
          )
        }),
      )
    })

    it('既存の荷主を示してどちらを使うか選ばせる', async () => {
      renderPage()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))

      // 「登録できません」で終わらせると、営業担当者は次に何をすればよいか分からない
      expect(await screen.findByText(/既に登録されています/)).toBeInTheDocument()
      expect(screen.getByText('SHP-000001')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '既存の荷主を使う' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'それでも新規で登録する' })).toBeInTheDocument()
    })

    it('既存を使うと一覧へ戻る', async () => {
      renderPage()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))
      await screen.findByText(/既に登録されています/)

      await userEvent.click(screen.getByRole('button', { name: '既存の荷主を使う' }))

      expect(await screen.findByRole('heading', { name: '荷主一覧' })).toBeInTheDocument()
    })

    it('それでも新規で登録すると別の荷主コードで登録される', async () => {
      renderPage()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))
      await screen.findByText(/既に登録されています/)

      await userEvent.click(screen.getByRole('button', { name: 'それでも新規で登録する' }))

      expect(await screen.findByText(/SHP-000002/)).toBeInTheDocument()
    })
  })
})
