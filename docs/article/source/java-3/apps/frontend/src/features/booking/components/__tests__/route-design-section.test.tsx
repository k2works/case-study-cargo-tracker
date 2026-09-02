import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../../../test/render'
import { ROUTING_STATUS_LABELS, type Booking } from '../../types'
import { RouteDesignSection } from '../route-design-section'

/**
 * 経路設計の枠（US09・[ADR-020]・[ADR-026] 決定 2）。
 *
 * <p><strong>経路の状況すべてに言葉がある。</strong>値を足したのに扱わないと、
 * 見出しだけの空の枠が出て「何か出るはずのものが出ていない」と読まれる
 * ——IT10 で `MISROUTED` を足したとき実際にそうなり、マニュアルのキャプチャを
 * 撮って初めて気づいた。
 *
 * <p><strong>名簿は書き写さない。</strong>`ROUTING_STATUS_LABELS`（画面が持つ経路の
 * 状況の一覧）から回す。値が増えたらこの検査が自動的にその値も見る。
 */
describe('経路設計の枠', () => {
  const BOOKING = {
    bookingId: 'BKG-2026000001',
    routingStatus: 'NOT_ROUTED',
    availableActions: [],
  } as unknown as Booking

  const issueTracking = {
    mutate: () => undefined,
    isPending: false,
  } as never

  it.each(Object.keys(ROUTING_STATUS_LABELS))(
    '経路の状況が %s でも、枠に言葉がある',
    (routingStatus) => {
      renderWithProviders(
        <RouteDesignSection
          booking={{ ...BOOKING, routingStatus } as Booking}
          isRoutingPlanner
          issueTracking={issueTracking}
        />,
      )

      const heading = screen.getByRole('heading', { name: '経路設計' })
      const body = heading.parentElement!.textContent?.replace('経路設計', '').trim()
      expect(
        body,
        `経路の状況 ${routingStatus} に言葉が無い。見出しだけの空の枠が出る`,
      ).not.toBe('')
    },
  )

  /** 経路設計者でなければ枠ごと出さない。**手番が違う**。 */
  it('経路設計者でなければ、枠を出さない', () => {
    renderWithProviders(
      <RouteDesignSection
        booking={BOOKING}
        isRoutingPlanner={false}
        issueTracking={issueTracking}
      />,
    )

    expect(screen.queryByRole('heading', { name: '経路設計' })).not.toBeInTheDocument()
  })
})
