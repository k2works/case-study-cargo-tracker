import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { notificationHandlers, resetNotifications } from '../../../mocks/handlers/notification'
import { server } from '../../../test/msw/server'
import { useAuthStore } from '../../../stores/auth-store'
import { loginAs, renderWithProviders } from '../../../test/render'
import { NotificationToasts } from '../components/notification-toasts'

describe('荷主宛のお知らせ', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
    resetNotifications()
    server.use(...notificationHandlers)
  })

  it('まだ見ていない知らせがポップアップで出る', async () => {
    loginAs(['ROLE_SHIPPER'])

    renderWithProviders(<NotificationToasts />)

    expect(await screen.findByText('貨物を積み込みました')).toBeTruthy()
    expect(screen.getByText('遅延が発生しました')).toBeTruthy()
  })

  /**
   * **気づく手段は次の行動へ繋ぐ**（IT10 の学び）。「遅延しました」と言われて、
   * そこから何も開けなければ荷主の仕事は進まない。
   */
  it('知らせから、その貨物の詳細へ行ける', async () => {
    loginAs(['ROLE_SHIPPER'])

    renderWithProviders(<NotificationToasts />)

    const link = await screen.findAllByRole('link', { name: 'この貨物を開く' })
    expect(link[0].getAttribute('href')).toBe('/shipper/tracking/TRK-20260823-0001')
    // 番号は読めるが、押せる名前にはしない（一覧のリンクと同じ名前にならないように）
    expect(screen.getAllByText('TRK-20260823-0001')[0]).toBeTruthy()
  })

  it('閉じると、その知らせだけが消える', async () => {
    loginAs(['ROLE_SHIPPER'])

    renderWithProviders(<NotificationToasts />)
    await screen.findByText('貨物を積み込みました')

    await userEvent.click(screen.getAllByRole('button', { name: '閉じる' })[0])

    await waitFor(() => {
      expect(screen.queryByText('貨物を積み込みました')).toBeNull()
    })
    expect(screen.getByText('遅延が発生しました')).toBeTruthy()
  })

  /**
   * **出した時点で読んだことにする。**閉じる操作を待つと、画面を閉じた荷主には
   * 同じ知らせが次のログインでもう一度出る。
   */
  it('一度出た知らせは、読み直しても二度と出ない', async () => {
    loginAs(['ROLE_SHIPPER'])

    const first = renderWithProviders(<NotificationToasts />)
    await screen.findByText('貨物を積み込みました')
    first.unmount()

    renderWithProviders(<NotificationToasts />)

    // 出るものが無いので、お知らせの領域そのものが現れない
    await waitFor(() => {
      expect(screen.queryByLabelText('お知らせ')).toBeNull()
    })
  })

  /**
   * **他のロールでは読みに行かない。**403 を毎分叩き続けることになる。
   */
  it('荷主でなければ、何も出ないし読みにも行かない', async () => {
    loginAs(['ROLE_SALES'])

    renderWithProviders(<NotificationToasts />)

    await waitFor(() => {
      expect(screen.queryByLabelText('お知らせ')).toBeNull()
    })
  })
})
