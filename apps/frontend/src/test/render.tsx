import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
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

/** テスト用の QueryClient。キャッシュの共有を確かめるテストは同じものを使い回す。 */
export function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

/**
 * React Query を使う画面のレンダリング。リトライは無効（テストが遅くなるだけ）。
 *
 * <p>既定では毎回新しい QueryClient を作る。キャッシュキーの取り違えを確かめるには
 * 同じ client を渡すこと。渡さないと「別のキャッシュを引いた」ことにならず、
 * キーを取り違えた実装でもテストが通る（IT2 で実際に素通りした）。
 */
export function renderWithProviders(
  ui: ReactNode,
  initialEntries: string[] = ['/'],
  client: QueryClient = createTestQueryClient(),
  options: { path?: string } = {},
) {
  // URL の一部を読む画面（詳細画面など）は、経路の型（/booking/:bookingId）を渡す。
  // 渡さないと useParams が空になり、画面の不具合と取り違える
  const content =
    options.path === undefined ? (
      ui
    ) : (
      <Routes>
        <Route path={options.path} element={ui} />
      </Routes>
    )

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={initialEntries}>{content}</MemoryRouter>
    </QueryClientProvider>,
  )
}
