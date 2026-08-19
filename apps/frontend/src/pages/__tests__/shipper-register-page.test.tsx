import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { ShipperRegisterPage } from '../shipper-register-page'

/** 遷移先の絞り込み条件を確かめるための補助。 */
function SearchParamsProbe() {
  const [params] = useSearchParams()
  return <div data-testid="search">{params.get('keyword') ?? ''}</div>
}

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
          <Route
            path="/booking/shippers"
            element={
              <>
                <h1>荷主一覧</h1>
                <SearchParamsProbe />
              </>
            }
          />
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

    it('既存を使うと、その荷主に絞り込んだ一覧へ移る', async () => {
      renderPage()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))
      await screen.findByText(/既に登録されています/)

      await userEvent.click(screen.getByRole('button', { name: '既存の荷主を使う' }))

      // 絞り込まずに戻すと、営業は用のある荷主を全件から探し直すことになる
      expect(await screen.findByRole('heading', { name: '荷主一覧' })).toBeInTheDocument()
      expect(screen.getByTestId('search')).toHaveTextContent('yamada@example.com')
    })

    it('判断できるよう既存荷主の種別も示す', async () => {
      renderPage()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))

      await screen.findByText(/既に登録されています/)
      // 個人か法人かは「同じ相手か別会社か」を判断する一番大きな手がかり。
      // 選択肢ではなく既存荷主の情報として出ていることを確かめる
      const term = screen.getByText('種別')
      expect(term.nextElementSibling).toHaveTextContent('個人')
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
