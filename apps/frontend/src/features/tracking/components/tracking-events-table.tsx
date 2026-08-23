import type { TrackingEvent } from '../types'

/**
 * 追跡の経過（US18-3）。
 *
 * **荷役の記録と手動更新が 1 本に並ぶ。** 別々に出すと、荷主は貨物に何が起きたかを
 * 2 つの表から組み立てることになる。並べ替えはサーバが行う。
 *
 * 公開画面と管理画面で同じものを使う。写すと、片方だけが古い形のまま残る。
 */
export function TrackingEventsTable({ events }: { events: TrackingEvent[] }) {
  if (events.length === 0) {
    return <p className="text-sm text-gray-600">まだ動きはありません。</p>
  }

  return (
    <table className="w-full text-left text-sm">
      <thead>
        <tr className="border-b border-gray-200 text-gray-600">
          <th className="py-2">日時</th>
          <th className="py-2">状態</th>
          <th className="py-2">場所</th>
        </tr>
      </thead>
      <tbody>
        {events.map((event) => (
          <tr key={`${event.occurredAt}-${event.status}`} className="border-b border-gray-100">
            <td className="py-2">{event.occurredAt}</td>
            <td className="py-2">{event.statusLabel}</td>
            <td className="py-2">{event.locationName}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
