import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchUnreadNotifications, markNotificationsRead } from './api'
import type { ShipperNotification } from './types'

/**
 * お知らせを読みに行く間隔。
 *
 * <p>**押し出す仕組み（WebSocket・SSE）を持ち込まない。**Gateway と認証の経路が増える。
 * 荷主が待っているのは「数分以内に気づける」ことで、秒単位の即時性ではない。
 * 短くしすぎると、画面を開いているだけで問い合わせが積み上がる。
 */
export const NOTIFICATION_POLL_INTERVAL_MS = 15_000

export const NOTIFICATION_QUERY_KEY = ['shipper', 'notifications'] as const

/**
 * まだ見ていないお知らせを一定間隔で読む。
 *
 * @param enabled 荷主としてログインしているときだけ true。**他のロールでは読みに行かない**
 *   ——403 を毎分叩き続けることになる
 */
export function useUnreadNotifications(enabled: boolean) {
  return useQuery({
    queryKey: NOTIFICATION_QUERY_KEY,
    queryFn: fetchUnreadNotifications,
    enabled,
    refetchInterval: enabled ? NOTIFICATION_POLL_INTERVAL_MS : false,
    // **画面に戻ったらすぐ読む。**別のタブで作業していた間の知らせを待たせない
    refetchOnWindowFocus: true,
  })
}

/** そこまで読んだことにする。 */
export function useMarkNotificationsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markNotificationsRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: NOTIFICATION_QUERY_KEY })
    },
  })
}

/** 一番新しい知らせの番号。読んだ位置をここまで進める。 */
export function latestNoticeId(notifications: ShipperNotification[]): number {
  return notifications.reduce((latest, notice) => Math.max(latest, notice.id), 0)
}
