import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TrackingPublicView } from './TrackingPublicView'
import type { TrackingInfo } from '../types/tracking'

function sampleData(): TrackingInfo {
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
    // SGSIN は現在位置 + 履歴セルに 2 回出るので getAllByText で確認
    expect(screen.getAllByText('SGSIN').length).toBeGreaterThan(0)
    expect(screen.getByTestId('tracking-event-table')).toBeInTheDocument()
    expect(screen.getByText('LOAD')).toBeInTheDocument()
    expect(screen.getByText('V-MOL-001')).toBeInTheDocument()
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
