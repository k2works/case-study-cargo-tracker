import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { formatBusinessDateTime } from '../../../lib/business-time'
import { useAuthStore } from '../../../stores/auth-store'
import { latestNoticeId, useMarkNotificationsRead, useUnreadNotifications } from '../queries'
import type { ShipperNotification } from '../types'

/**
 * 荷主宛のお知らせを、画面の隅にポップアップで出す（US39）。
 *
 * <p>**気づく手段は次の行動へ繋ぐ**（IT10 の学び）。文言を出すだけでなく、
 * その貨物の詳細へ行けるようにする——「遅延しました」と言われて、そこから
 * 何も開けなければ荷主の仕事は進まない。
 *
 * <p>**読んだことにするのは、出したときである。**閉じる操作を待つと、
 * 画面を閉じた荷主には同じ知らせが次のログインでもう一度出る。
 */
export function NotificationToasts() {
  const isShipper = useAuthStore((state) => state.hasAnyRole(['ROLE_SHIPPER']))
  const { data } = useUnreadNotifications(isShipper)
  const markRead = useMarkNotificationsRead()
  const [shown, setShown] = useState<ShipperNotification[]>([])
  const [dismissed, setDismissed] = useState<number[]>([])

  const unread = data?.notifications ?? []
  const latest = latestNoticeId(unread)

  useEffect(() => {
    if (latest === 0) {
      return
    }
    setShown((current) => {
      const known = new Set(current.map((notice) => notice.id))
      const added = unread.filter((notice) => !known.has(notice.id))
      return added.length === 0 ? current : [...current, ...added]
    })
    // **出した時点で読んだことにする。**閉じる操作を待つと、画面を閉じた荷主には
    // 同じ知らせが次のログインでもう一度出る
    markRead.mutate(latest)
    // markRead を依存に入れると、mutate のたびに新しい関数が来て無限に走る
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [latest])

  const undismissed = shown.filter((notice) => !dismissed.includes(notice.id))
  /**
   * 同時に出す数（IT16 レビュー 中 6）。
   *
   * **20 件を縦に積むと画面が知らせで覆われる。**幅 320px の札が 20 枚並ぶと、
   * 見えるのは 5 件ほどで、残りは既読のまま誰にも読まれない。
   * あふれた分は件数で伝え、貨物の詳細のお知らせ欄で読んでもらう。
   */
  const AT_ONCE = 4
  const visible = undismissed.slice(0, AT_ONCE)
  const overflow = undismissed.length - visible.length
  if (visible.length === 0) {
    return null
  }

  return (
    <div
      // 画面の操作を邪魔しない位置に置く。**読み上げは割り込ませない**
      // ——入力中の荷主の集中を切らないため polite にする
      aria-live="polite"
      aria-label="お知らせ"
      className="fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-2"
    >
      {visible.map((notice) => (
        <article
          key={notice.id}
          className="rounded border border-blue-300 bg-white p-3 shadow-lg"
        >
          <div className="flex items-start justify-between gap-2">
            <p className="text-sm font-medium text-gray-900">{notice.message}</p>
            <button
              type="button"
              aria-label="閉じる"
              onClick={() => setDismissed((current) => [...current, notice.id])}
              className="shrink-0 text-gray-500 hover:text-gray-800"
            >
              ×
            </button>
          </div>
          {/*
            **追跡番号を、押せる名前にしない。**一覧にも同じ番号のリンクがあり、
            同じ名前の入口が 2 つできる（E2E が strict mode で捕まえた）。
            番号は読むもの、リンクは行くものとして分ける
          */}
          {/* **いつの話かを添える**（IT16 レビュー 中 7）。朝ログインして
              「問題が発生しました」だけ出たとき、荷主が最初に聞くのは
              「それはいつですか」である */}
          <p className="mt-1 text-xs text-gray-600">
            {formatBusinessDateTime(notice.noticedAt)}
          </p>
          <p className="font-mono text-xs text-gray-600">{notice.trackingNumber}</p>
          <Link
            to={`/shipper/tracking/${encodeURIComponent(notice.trackingNumber)}`}
            className="mt-1 inline-block text-sm text-blue-700 underline"
          >
            この貨物を開く
          </Link>
        </article>
      ))}
      {overflow > 0 && (
        <p className="rounded border border-gray-300 bg-white p-2 text-xs text-gray-700">
          {`他に ${overflow} 件のお知らせがあります。貨物の詳細で読めます。`}
        </p>
      )}
    </div>
  )
}
