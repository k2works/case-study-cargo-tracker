import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { HandlingActivityForm } from './HandlingActivityForm'

const mutateMock = vi.fn()
let pendingState = false

vi.mock('../hooks/useHandling', () => ({
  useRegisterHandlingActivity: () => ({
    mutate: mutateMock,
    get isPending() {
      return pendingState
    },
  }),
}))

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <HandlingActivityForm />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('HandlingActivityForm', () => {
  beforeEach(() => {
    mutateMock.mockReset()
    pendingState = false
  })

  it('US15 受入1-4: 必須フィールドが表示される', () => {
    renderForm()
    expect(screen.getByLabelText('追跡番号')).toBeInTheDocument()
    expect(screen.getByLabelText('作業種別')).toBeInTheDocument()
    expect(screen.getByLabelText('作業場所（UN/LOCODE）')).toBeInTheDocument()
    expect(screen.getByLabelText('作業日時')).toBeInTheDocument()
    expect(screen.getByLabelText('作業員 ID')).toBeInTheDocument()
  })

  it('US15 受入2: 作業種別に 5 種類（受領・積込・荷降し・引取・税関通過）が選択肢として表示される', () => {
    renderForm()
    const select = screen.getByTestId('handling-type-select') as HTMLSelectElement
    const options = Array.from(select.options).map((o) => o.value)
    expect(options).toEqual(['RECEIVE', 'LOAD', 'UNLOAD', 'CLAIM', 'CUSTOMS'])
  })

  it('US15 受入3: LOAD 種別を選択すると航海番号フィールドが表示される', async () => {
    renderForm()
    expect(screen.queryByTestId('handling-voyage-section')).not.toBeInTheDocument()
    const user = userEvent.setup()
    await user.selectOptions(screen.getByTestId('handling-type-select'), 'LOAD')
    expect(screen.getByTestId('handling-voyage-section')).toBeInTheDocument()
  })

  it('US16 受入1: CLAIM 種別を選択すると荷受人確認フィールドが表示される', async () => {
    renderForm()
    expect(screen.queryByTestId('handling-claim-section')).not.toBeInTheDocument()
    const user = userEvent.setup()
    await user.selectOptions(screen.getByTestId('handling-type-select'), 'CLAIM')
    expect(screen.getByTestId('handling-claim-section')).toBeInTheDocument()
    expect(screen.getByTestId('claim-consignee-input')).toBeInTheDocument()
    expect(screen.getByTestId('claim-code-input')).toBeInTheDocument()
  })

  it('US16: CLAIM 選択時に確認方法を「署名画像」に切り替えると署名 URI 入力欄に変わる', async () => {
    renderForm()
    const user = userEvent.setup()
    await user.selectOptions(screen.getByTestId('handling-type-select'), 'CLAIM')
    expect(screen.getByTestId('claim-code-input')).toBeInTheDocument()
    expect(screen.queryByTestId('claim-signature-input')).not.toBeInTheDocument()

    await user.click(screen.getByTestId('claim-method-signature'))
    expect(screen.queryByTestId('claim-code-input')).not.toBeInTheDocument()
    expect(screen.getByTestId('claim-signature-input')).toBeInTheDocument()
  })

  it('US16: CLAIM + 確認コード入力で mutate に claimVerification.confirmationCode が含まれる', async () => {
    renderForm()
    const user = userEvent.setup()
    await user.type(screen.getByTestId('handling-tracking-number-input'), 'TRK-20260810-CLAIM001')
    await user.selectOptions(screen.getByTestId('handling-type-select'), 'CLAIM')
    await user.type(screen.getByTestId('handling-unlocode-input'), 'deham')
    await user.type(screen.getByTestId('handling-occurred-at-input'), '2026-08-10T14:30')
    await user.type(screen.getByTestId('handling-operator-input'), 'handler-002')
    await user.type(screen.getByTestId('claim-consignee-input'), 'John Doe')
    await user.type(screen.getByTestId('claim-code-input'), 'AX9-2K7')
    await user.click(screen.getByTestId('handling-submit'))

    expect(mutateMock).toHaveBeenCalledTimes(1)
    const request = mutateMock.mock.calls[0][0]
    expect(request.handlingType).toBe('CLAIM')
    expect(request.claimVerification).toEqual({
      consigneeName: 'John Doe',
      confirmationCode: 'AX9-2K7',
    })
    expect(request.claimVerification.signatureRef).toBeUndefined()
  })

  it('US15: 送信時に mutate へ整形済みリクエストを渡す', async () => {
    renderForm()
    const user = userEvent.setup()
    await user.type(screen.getByTestId('handling-tracking-number-input'), 'TRK-20260720-ABC12345')
    await user.type(screen.getByTestId('handling-unlocode-input'), 'jptyo')
    await user.type(screen.getByTestId('handling-occurred-at-input'), '2026-07-20T09:00')
    await user.type(screen.getByTestId('handling-operator-input'), 'handler-001')
    await user.click(screen.getByTestId('handling-submit'))

    expect(mutateMock).toHaveBeenCalledTimes(1)
    const request = mutateMock.mock.calls[0][0]
    expect(request.trackingNumber).toBe('TRK-20260720-ABC12345')
    expect(request.handlingType).toBe('RECEIVE')
    expect(request.unlocode).toBe('JPTYO') // 大文字に変換
    expect(request.operatorId).toBe('handler-001')
  })
})
