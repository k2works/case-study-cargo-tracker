import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, it, expect } from 'vitest'
import { TrackingList } from './TrackingList'
import type { TrackingListItem } from '../types/tracking'

function sample(overrides: Partial<TrackingListItem> = {}): TrackingListItem {
  return {
    trackingNumber: 'TRK-A1B2C3D4E5',
    bookingId: 'B-001',
    currentStatus: 'IN_TRANSIT',
    currentUnlocode: 'SGSIN',
    originUnlocode: 'JPTYO',
    destinationUnlocode: 'DEHAM',
    estimatedArrival: '2026-08-10T14:30:00',
    deliveredAt: null,
    misrouted: false,
    lastEventAt: '2026-07-25T08:00:00',
    updatedAt: '2026-07-25T08:00:00',
    ...overrides,
  }
}

function renderWithRouter(ui: React.ReactNode) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('TrackingList', () => {
  it('ローディング中: スピナーを表示', () => {
    renderWithRouter(<TrackingList items={undefined} isLoading={true} error={null} />)
    expect(screen.getByRole('status')).toHaveTextContent('読み込み中')
  })

  it('エラー時: アラートを表示', () => {
    renderWithRouter(
      <TrackingList items={undefined} isLoading={false} error={new Error('取得失敗')} />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('取得失敗')
  })

  it('空のとき: メッセージを表示', () => {
    renderWithRouter(<TrackingList items={[]} isLoading={false} error={null} />)
    expect(screen.getByTestId('tracking-list-empty')).toBeInTheDocument()
  })

  it('追跡番号リンクで S17 (/tracking/:tn/manage) へ遷移する href を持つ', () => {
    renderWithRouter(
      <TrackingList items={[sample()]} isLoading={false} error={null} />,
    )
    const link = screen.getByRole('link', { name: 'TRK-A1B2C3D4E5' })
    expect(link).toHaveAttribute('href', '/tracking/TRK-A1B2C3D4E5/manage')
  })

  it('現在状態を日本語ラベルで表示する', () => {
    renderWithRouter(
      <TrackingList items={[sample({ currentStatus: 'DELIVERED' })]} isLoading={false} error={null} />,
    )
    expect(screen.getByText('引取済')).toBeInTheDocument()
  })

  it('誤配送行は bg-red-50 でハイライトされる', () => {
    renderWithRouter(
      <TrackingList items={[sample({ misrouted: true })]} isLoading={false} error={null} />,
    )
    const row = screen.getByTestId('tracking-list-row-TRK-A1B2C3D4E5')
    expect(row.className).toContain('bg-red-50')
  })

  it('複数件を表示する', () => {
    renderWithRouter(
      <TrackingList
        items={[sample(), sample({ trackingNumber: 'TRK-Z9Y8X7W6V5', bookingId: 'B-002' })]}
        isLoading={false}
        error={null}
      />,
    )
    expect(screen.getByText('TRK-A1B2C3D4E5')).toBeInTheDocument()
    expect(screen.getByText('TRK-Z9Y8X7W6V5')).toBeInTheDocument()
  })
})
