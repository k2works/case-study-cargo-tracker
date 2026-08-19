import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import { ForbiddenPage } from '../forbidden-page'

describe('権限エラー画面', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('認証済みならダッシュボードへ戻れる（行き止まりにしない）', () => {
    useAuthStore.getState().login({
      token: 't',
      userId: 'u01',
      displayName: 'テスト利用者',
      roles: ['ROLE_HANDLER'],
    })

    render(
      <MemoryRouter>
        <ForbiddenPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: /ダッシュボード/ })).toHaveAttribute(
      'href',
      '/dashboard',
    )
  })

  it('未認証ならログインへ戻れる', () => {
    render(
      <MemoryRouter>
        <ForbiddenPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: /ログイン/ })).toHaveAttribute('href', '/login')
  })
})
