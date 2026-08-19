import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
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

  it('追跡照会は US18 の実装まで受け付けない旨を示す', () => {
    render(
      <MemoryRouter>
        <PortalPage />
      </MemoryRouter>,
    )

    // 押せるのに何も起きない入力欄は、動かないのか自分の入力が悪いのか区別できない
    expect(screen.getByLabelText('追跡番号')).toBeDisabled()
    expect(screen.getByText(/準備中/)).toBeInTheDocument()
  })
})
