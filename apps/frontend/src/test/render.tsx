import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { useAuthStore } from '../stores/auth-store'
import type { Role } from '../types/role'

/**
 * テストの定型（ログイン状態の用意・QueryClient の生成・ルーターの用意）。
 *
 * <p>各テストに書き写すと、認証の持たせ方が少しずつ違う写しが増えて、
 * 「このテストは何を前提にしているか」が読み取りにくくなる。
 */
export function loginAs(roles: Role[], displayName = 'テスト利用者') {
  useAuthStore.getState().login({
    token: 'test-token',
    userId: 'test01',
    displayName,
    roles,
  })
}

/** React Query を使う画面のレンダリング。リトライは無効（テストが遅くなるだけ）。 */
export function renderWithProviders(ui: ReactNode, initialEntries: string[] = ['/']) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}
