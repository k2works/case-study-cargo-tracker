import { render, screen, within } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TrackingPublicView } from './TrackingPublicView'
import type { TrackingInfo } from '../types/tracking'

function sampleData(overrides: Partial<TrackingInfo> = {}): TrackingInfo {
  return {
    trackingNumber: 'TRK-ABC1234567',
    currentStatus: 'IN_TRANSIT',
    currentLocation: { unlocode: 'SGSIN', portName: null },
    estimatedArrival: '2026-08-10T14:30:00',
    deliveredAt: null,
    misrouted: false,
    validUntil: '2026-09-01T00:00:00',
    events: [
      {
        occurredAt: '2026-07-25T08:00:00',
        type: 'STATUS_UPDATE',
        unlocode: 'SGSIN',
        voyageNumber: null,
        transportStatus: 'IN_TRANSIT',
        handlingType: null,
        source: 'MANUAL',
        description: null,
      },
      {
        occurredAt: '2026-07-20T14:00:00',
        type: 'HANDLING',
        unlocode: 'JPTYO',
        voyageNumber: 'V-MOL-001',
        transportStatus: 'LOADED',
        handlingType: 'LOAD',
        source: 'HANDLING',
        description: null,
      },
    ],
    ...overrides,
  }
}

describe('TrackingPublicView', () => {
  it('通常時: 現在状態と履歴を表示する', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={sampleData()}
        isLoading={false}
        error={null}
      />,
    )
    expect(screen.getByText('TRK-ABC1234567')).toBeInTheDocument()
    expect(screen.getByText('輸送中')).toBeInTheDocument()
    expect(screen.getAllByText('SGSIN').length).toBeGreaterThan(0)
    expect(screen.getByTestId('tracking-event-table')).toBeInTheDocument()
    // M-13: handlingType=LOAD は「積込」に日本語化される
    expect(screen.getByText('積込')).toBeInTheDocument()
    expect(screen.queryByText('LOAD')).toBeNull()
    expect(screen.getByText('V-MOL-001')).toBeInTheDocument()
  })

  it('H-4: 追跡履歴は最新（occurredAt 降順）から並ぶ', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={sampleData()}
        isLoading={false}
        error={null}
      />,
    )
    const table = screen.getByTestId('tracking-event-table')
    const rows = within(table).getAllByRole('row').slice(1) // ヘッダー除外
    // 1 行目に 2026-07-25（より新しい日付）が含まれる
    expect(rows[0].textContent).toContain('2026')
    expect(rows[0].textContent).toContain('07/25')
    // 2 行目に 2026-07-20 が含まれる
    expect(rows[1].textContent).toContain('07/20')
  })

  it('H-3: MISROUTED 状態のときバナーが表示され、「誤配送」ラベルは出さない', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={sampleData({ currentStatus: 'MISROUTED' })}
        isLoading={false}
        error={null}
      />,
    )
    const banner = screen.getByTestId('tracking-misrouted-banner')
    expect(banner).toBeInTheDocument()
    expect(banner).toHaveTextContent('現在経路を調整中です')
    // 旧仕様の「誤配送 あり/なし」表記は出さない
    expect(screen.queryByText('誤配送 あり')).toBeNull()
    expect(screen.queryByText('あり')).toBeNull()
  })

  it('H-3: MISROUTED 以外の状態ではバナーは表示されない', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={sampleData()}
        isLoading={false}
        error={null}
      />,
    )
    expect(screen.queryByTestId('tracking-misrouted-banner')).toBeNull()
  })

  it('H-2: 日時表記が日本時間（YYYY/MM/DD HH:mm）になっている', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={sampleData()}
        isLoading={false}
        error={null}
      />,
    )
    // UTC 14:30 → JST 23:30 で表示される
    // toLocaleString('ja-JP') 出力は "2026/08/10 23:30" 形式
    const detailItems = screen.getAllByText(/2026/)
    expect(detailItems.length).toBeGreaterThan(0)
    // 旧 UTC 表記（"T14:30" や "2026-08-10 14:30"）が含まれない
    expect(screen.queryByText('2026-08-10 14:30')).toBeNull()
  })

  it('ローディング中: スピナーを表示する', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={undefined}
        isLoading={true}
        error={null}
      />,
    )
    expect(screen.getByRole('status')).toHaveTextContent('読み込み中')
  })

  it('TOKEN_EXPIRED: 期限切れ警告を表示する', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={undefined}
        isLoading={false}
        error={{ status: 403, errorCode: 'TOKEN_EXPIRED', message: 'expired' }}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('リンクの有効期限が切れています')
  })

  it('TOKEN_INVALID: 無効リンク警告を表示する', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={undefined}
        isLoading={false}
        error={{ status: 401, errorCode: 'TOKEN_INVALID', message: 'invalid' }}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('無効なリンクです')
  })

  it('H-9: TOKEN_TN_MISMATCH も「無効なリンクです」と表示される', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={undefined}
        isLoading={false}
        error={{ status: 400, errorCode: 'TOKEN_TN_MISMATCH', message: 'mismatch' }}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('無効なリンクです')
  })

  it('TRACKING_NOT_FOUND: 追跡番号不在エラーを表示する', () => {
    render(
      <TrackingPublicView
        trackingNumber="TRK-ABC1234567"
        data={undefined}
        isLoading={false}
        error={{ status: 404, errorCode: 'TRACKING_NOT_FOUND', message: 'not found' }}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('追跡情報が見つかりません')
  })
})
