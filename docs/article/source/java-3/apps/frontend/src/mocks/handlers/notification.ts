import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import type { ShipperNotification } from '../../features/notification/types'

/**
 * 荷主宛のお知らせのモック（US39）。
 *
 * **読んだ位置はサーバが覚える**——モックも同じ形にする。画面側で覚える作りにすると、
 * 本物と食い違う形のまま緑になる（[記憶]「モックを本物より甘くしない」）。
 */
let lastNoticeId = 0

const allNotifications: ShipperNotification[] = [
  {
    id: 1,
    trackingNumber: 'TRK-20260823-0001',
    noticedAt: '2026-09-01T00:00:00Z',
    message: '貨物を積み込みました',
  },
  {
    id: 2,
    trackingNumber: 'TRK-20260823-0001',
    noticedAt: '2026-09-01T01:00:00Z',
    message: '遅延が発生しました',
  },
]

/** テストごとに読んだ位置を戻す。**戻さないと、前のテストの既読が次に効く**。 */
export function resetNotifications() {
  lastNoticeId = 0
}

export const notificationHandlers = [
  http.get(API_PATHS.shipperNotifications, () =>
    HttpResponse.json({
      notifications: allNotifications.filter((notice) => notice.id > lastNoticeId),
    }),
  ),

  http.post(API_PATHS.shipperNotificationsRead, async ({ request }) => {
    const body = (await request.json()) as { lastNoticeId: number }
    // **戻さない。**本物（NoticeWatermark#advanceTo）と同じ規則にする
    lastNoticeId = Math.max(lastNoticeId, body.lastNoticeId)
    return new HttpResponse(null, { status: 204 })
  }),
]
