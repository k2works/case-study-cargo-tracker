import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TrackingTokenIssuer } from './TrackingTokenIssuer'

const mutateMock = vi.fn()
let pendingState = false

vi.mock('../hooks/useTracking', () => ({
  useIssueTrackingToken: () => ({
    mutate: (
      trackingNumber: string,
      options?: {
        onSuccess?: (response: { url: string; token: string; validUntil: string }) => void
        onError?: (error: Error) => void
      },
    ) => mutateMock(trackingNumber, options),
    get isPending() {
      return pendingState
    },
  }),
}))

function renderComponent(props: Parameters<typeof TrackingTokenIssuer>[0]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TrackingTokenIssuer {...props} />
    </QueryClientProvider>,
  )
}

describe('TrackingTokenIssuer', () => {
  beforeEach(() => {
    mutateMock.mockReset()
    pendingState = false
  })

  it('発行ボタンを押すと mutate が呼ばれる', async () => {
    const user = userEvent.setup()
    renderComponent({ trackingNumber: 'TRK-ABC1234567', variant: 'issue' })

    await user.click(screen.getByTestId('issue-tracking-token-button'))
    expect(mutateMock).toHaveBeenCalledWith('TRK-ABC1234567', expect.any(Object))
  })

  it('成功時に URL と有効期限を表示する', async () => {
    const user = userEvent.setup()
    mutateMock.mockImplementation((_trackingNumber, options) => {
      options?.onSuccess?.({
        url: '/tracking/TRK-ABC1234567?token=test.jwt.token',
        token: 'test.jwt.token',
        validUntil: '2026-09-01T00:00:00',
      })
    })

    renderComponent({ trackingNumber: 'TRK-ABC1234567', recipientEmail: 'yamada@example.com' })

    await user.click(screen.getByTestId('issue-tracking-token-button'))
    await waitFor(() => {
      expect(screen.getByTestId('tracking-token-success')).toBeInTheDocument()
    })
    expect(screen.getByTestId('tracking-token-success')).toHaveTextContent(
      'TRK-ABC1234567?token=test.jwt.token',
    )
    expect(screen.getByTestId('tracking-token-success')).toHaveTextContent('2026-09-01T00:00:00')
    expect(screen.getByText('yamada@example.com')).toBeInTheDocument()
  })

  it('発行成功後にコピーボタンが表示される', async () => {
    const user = userEvent.setup()
    mutateMock.mockImplementation((_trackingNumber, options) => {
      options?.onSuccess?.({
        url: '/tracking/TRK-ABC1234567?token=test.jwt.token',
        token: 'test.jwt.token',
        validUntil: '2026-09-01T00:00:00',
      })
    })

    renderComponent({ trackingNumber: 'TRK-ABC1234567' })

    await user.click(screen.getByTestId('issue-tracking-token-button'))
    await waitFor(() => {
      expect(screen.getByTestId('copy-tracking-url-button')).toBeInTheDocument()
    })
  })

  it('reissue バリアントでは再発行ラベルが表示される', () => {
    renderComponent({ trackingNumber: 'TRK-ABC1234567', variant: 'reissue' })
    expect(screen.getByText('追跡トークン再発行')).toBeInTheDocument()
    expect(screen.getByTestId('issue-tracking-token-button')).toHaveTextContent(
      '追跡トークンを再発行',
    )
  })

  it('エラー時にエラーメッセージを表示する', async () => {
    const user = userEvent.setup()
    mutateMock.mockImplementation((_trackingNumber, options) => {
      options?.onError?.(new Error('発行に失敗しました'))
    })

    renderComponent({ trackingNumber: 'TRK-ABC1234567' })

    await user.click(screen.getByTestId('issue-tracking-token-button'))
    await waitFor(() => {
      expect(screen.getByTestId('tracking-token-error')).toHaveTextContent('発行に失敗しました')
    })
  })
})
