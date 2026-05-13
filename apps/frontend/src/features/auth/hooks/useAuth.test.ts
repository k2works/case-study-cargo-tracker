import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { createElement } from 'react'
import { useLogin, useLogout } from './useAuth'
import * as apiClient from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/authStore'

vi.mock('../../../lib/api-client', () => ({
  apiClient: {
    post: vi.fn(),
  },
}))

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>()
  return {
    ...actual,
    useNavigate: () => vi.fn(),
  }
})

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(MemoryRouter, null, children),
    )
}

describe('useLogin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    useAuthStore.setState({ token: null, user: null })
  })

  it('ログイン成功時にトークンが保存される', async () => {
    const mockResponse = {
      token: 'test-token',
      username: 'admin',
      roles: ['ADMIN'],
    }
    vi.mocked(apiClient.apiClient.post).mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useLogin(), { wrapper: createWrapper() })

    result.current.mutate({ username: 'admin', password: 'password' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(useAuthStore.getState().token).toBe('test-token')
  })

  it('ログイン失敗時に isError が true になる', async () => {
    vi.mocked(apiClient.apiClient.post).mockRejectedValue(new Error('Unauthorized'))

    const { result } = renderHook(() => useLogin(), { wrapper: createWrapper() })

    result.current.mutate({ username: 'admin', password: 'wrong' })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useLogout', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useAuthStore.setState({ token: null, user: null })
  })

  it('ログアウト時にストアがクリアされる', () => {
    useAuthStore.getState().setAuth('token', { username: 'admin', roles: ['ADMIN'] })

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() })
    result.current()

    expect(useAuthStore.getState().token).toBeNull()
  })
})
