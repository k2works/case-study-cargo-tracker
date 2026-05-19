import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrackingExceptionList } from './TrackingExceptionList'
import type { TrackingException } from '../types/tracking'

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

const mockResolveMutate = vi.fn()

vi.mock('../hooks/useTracking', () => ({
  useTrackingExceptions: () => ({
    data: [
      {
        exceptionId: 'exc-001',
        trackingNumber: 'TRK-001',
        exceptionType: 'DELAY',
        occurredAt: '2026-07-28T10:00:00',
        occurredUnlocode: 'JPTYO',
        description: '遅延が発生しました',
        responseStatus: 'PENDING',
        resolution: null,
        resolvedAt: null,
        escalated: false,
        createdAt: null,
      } satisfies TrackingException,
      {
        exceptionId: 'exc-002',
        trackingNumber: 'TRK-001',
        exceptionType: 'LOSS',
        occurredAt: '2026-07-29T10:00:00',
        occurredUnlocode: 'SGSIN',
        description: '紛失が発生しました',
        responseStatus: 'RESOLVED',
        resolution: '補償完了',
        resolvedAt: '2026-07-30T09:00:00',
        escalated: true,
        createdAt: null,
      } satisfies TrackingException,
    ],
    isLoading: false,
    isError: false,
  }),
  useResolveTrackingException: () => ({
    mutate: mockResolveMutate,
    isPending: false,
    isError: false,
  }),
}))

describe('TrackingExceptionList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('例外一覧が表示される', () => {
    render(<TrackingExceptionList trackingNumber="TRK-001" />, { wrapper })
    expect(screen.getByText('遅延が発生しました')).toBeInTheDocument()
    expect(screen.getByText('紛失が発生しました')).toBeInTheDocument()
  })

  it('PENDING 例外には解決ボタンが表示される', () => {
    render(<TrackingExceptionList trackingNumber="TRK-001" />, { wrapper })
    expect(screen.getByRole('button', { name: /解決/ })).toBeInTheDocument()
  })

  it('RESOLVED 例外には解決ボタンが表示されない', () => {
    render(<TrackingExceptionList trackingNumber="TRK-001" />, { wrapper })
    const resolveButtons = screen.queryAllByRole('button', { name: /解決/ })
    expect(resolveButtons).toHaveLength(1)
  })

  it('escalated=true の例外には緊急マークが表示される', () => {
    render(<TrackingExceptionList trackingNumber="TRK-001" />, { wrapper })
    expect(screen.getByText(/緊急/)).toBeInTheDocument()
  })
})
