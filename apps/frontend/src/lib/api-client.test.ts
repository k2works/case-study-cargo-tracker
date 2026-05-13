import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ApiError, authApiClient, bookingApiClient } from './api-client'
import { useAuthStore } from '../stores/authStore'

describe('ApiError', () => {
  it('status と message が設定される', () => {
    const error = new ApiError(404, 'Not Found')
    expect(error.status).toBe(404)
    expect(error.message).toBe('Not Found')
    expect(error.name).toBe('ApiError')
  })
})

describe('apiClient', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useAuthStore.setState({ token: null, user: null })
    vi.restoreAllMocks()
  })

  it('GET リクエストが成功する', async () => {
    const mockData = { id: 1, name: 'test' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockData),
    }))

    const result = await authApiClient.get<typeof mockData>('/api/test')
    expect(result).toEqual(mockData)
  })

  it('POST リクエストが成功する', async () => {
    const mockData = { id: 1 }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockData),
    }))

    const result = await authApiClient.post<typeof mockData>('/api/test', { name: 'test' })
    expect(result).toEqual(mockData)
  })

  it('401 エラー時にログアウトして ApiError をスローする', async () => {
    useAuthStore.getState().setAuth('token', { username: 'admin', roles: ['ADMIN'] })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({}),
    }))

    await expect(authApiClient.get('/api/test')).rejects.toThrow(ApiError)
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('その他のエラー時に ApiError をスローする', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: () => Promise.resolve({ message: 'Server Error' }),
    }))

    await expect(authApiClient.get('/api/test')).rejects.toThrow('Server Error')
  })

  it('bookingApiClient の GET リクエストが成功する', async () => {
    const mockData = [{ id: 1 }]
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockData),
    }))

    const result = await bookingApiClient.get<typeof mockData>('/api/v1/shippers')
    expect(result).toEqual(mockData)
  })

  it('PUT リクエストが成功する', async () => {
    const mockData = { id: 1, updated: true }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockData),
    }))

    const result = await authApiClient.put<typeof mockData>('/api/test', { name: 'updated' })
    expect(result).toEqual(mockData)
  })

  it('DELETE リクエストが成功する', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    }))

    await expect(authApiClient.delete('/api/test')).resolves.toBeDefined()
  })

  it('bookingApiClient の PUT リクエストが成功する', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ id: 1 }),
    }))

    await expect(bookingApiClient.put('/api/v1/shippers/1', {})).resolves.toBeDefined()
  })

  it('bookingApiClient の DELETE リクエストが成功する', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    }))

    await expect(bookingApiClient.delete('/api/v1/shippers/1')).resolves.toBeDefined()
  })

  it('Authorization ヘッダーにトークンが付与される', async () => {
    useAuthStore.getState().setAuth('my-token', { username: 'admin', roles: ['ADMIN'] })
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    })
    vi.stubGlobal('fetch', mockFetch)

    await authApiClient.get('/api/test')

    const [, options] = mockFetch.mock.calls[0] as [string, RequestInit]
    const headers = options.headers as Record<string, string>
    expect(headers['Authorization']).toBe('Bearer my-token')
  })
})
