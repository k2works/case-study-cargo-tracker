import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type { ShipperNotifications } from './types'

/** まだ見ていないお知らせ。**古い順**——起きた順に出す。 */
export function fetchUnreadNotifications(): Promise<ShipperNotifications> {
  return apiClient.get<ShipperNotifications>(API_PATHS.shipperNotifications)
}

/**
 * そこまで読んだことにする。
 *
 * **読んだ位置はサーバが覚える。**ブラウザに持つと、別の端末で同じ知らせがもう一度出る
 * ——荷主は自宅の PC と現場の端末を使い分ける。
 */
export function markNotificationsRead(lastNoticeId: number): Promise<void> {
  return apiClient.post<void>(API_PATHS.shipperNotificationsRead, { lastNoticeId })
}
