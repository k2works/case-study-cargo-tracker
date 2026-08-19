import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { useAuthStore } from '../../stores/auth-store'
import { server } from '../../test/msw/server'
import { ShipperListPage } from '../shipper-list-page'

const SHIPPERS = [
  {
    id: 1,
    shipperCode: 'SHP-000001',
    type: 'INDIVIDUAL',
    name: '山田太郎',
    email: 'yamada@example.com',
    address: '東京都千代田区 1-1-1',
    phone: '03-1234-5678',
  },
]

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShipperListPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('荷主一覧', () => {
  beforeEach(() => {
    useAuthStore.getState().login({
      token: 't',
      userId: 'sales01',
      displayName: '営業担当',
      roles: ['ROLE_SALES'],
    })
    server.use(http.get(API_PATHS.shippers, () => HttpResponse.json(SHIPPERS)))
  })

  it('登録済みの荷主を一覧で示す', async () => {
    renderPage()

    expect(await screen.findByText('山田太郎')).toBeInTheDocument()
    expect(screen.getByText('SHP-000001')).toBeInTheDocument()
    // ROLE ではなく業務上の呼び名で示す
    expect(screen.getByText('個人')).toBeInTheDocument()
  })

  it('キーワードで絞り込める', async () => {
    renderPage()
    await screen.findByText('山田太郎')

    server.use(
      http.get(API_PATHS.shippers, ({ request }) => {
        const keyword = new URL(request.url).searchParams.get('keyword')
        return HttpResponse.json(keyword === '伊藤' ? [] : SHIPPERS)
      }),
    )

    await userEvent.type(screen.getByLabelText('荷主を探す'), '伊藤')
    await userEvent.click(screen.getByRole('button', { name: '検索' }))

    expect(await screen.findByText(/見つかりません/)).toBeInTheDocument()
  })

  it('荷主登録への導線を置く', async () => {
    renderPage()
    await screen.findByText('山田太郎')

    expect(screen.getByRole('link', { name: /荷主を登録/ })).toHaveAttribute(
      'href',
      '/booking/shippers/new',
    )
  })
})
