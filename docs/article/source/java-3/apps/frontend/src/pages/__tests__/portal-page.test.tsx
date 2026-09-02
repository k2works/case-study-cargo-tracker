import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { PortalPage } from '../portal-page'

describe('ポータル', () => {
  it('業務利用者のためのログイン導線を置く', () => {
    render(
      <MemoryRouter>
        <PortalPage />
      </MemoryRouter>,
    )

    // 認証済み利用者にしか働かない導線では、まだログインしていない人が入口を見つけられない
    expect(screen.getByRole('link', { name: /ログイン/ })).toHaveAttribute('href', '/login')
  })

  /**
   * US18-5。**ログインなしで照会できる。**
   *
   * ロール別の到達性は認証済み利用者にしか働かない。荷主はログインしないので、
   * 入口を認証の外に置く（IT7 の学び）。
   */
  it('追跡番号を入れると、公開の追跡照会へ移る', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<PortalPage />} />
          <Route path="/tracking/:trackingNumber" element={<div>追跡の画面</div>} />
        </Routes>
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('追跡番号'), 'TRK-20260823-0001')
    await user.click(screen.getByRole('button', { name: '追跡する' }))

    expect(await screen.findByText('追跡の画面')).toBeInTheDocument()
  })

  /** 空のまま押しても、番号の無い URL へ飛ばさない。 */
  it('追跡番号が空のままでは移らない', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<PortalPage />} />
          <Route path="/tracking/:trackingNumber" element={<div>追跡の画面</div>} />
        </Routes>
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: '追跡する' }))

    expect(screen.queryByText('追跡の画面')).not.toBeInTheDocument()
  })
})
