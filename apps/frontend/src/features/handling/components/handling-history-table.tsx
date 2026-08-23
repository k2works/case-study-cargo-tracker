import type { HandlingActivity } from '../types'
import { formatBusinessDateTime } from '../../../lib/business-time'
import { useHandlingTypes } from '../queries'

/**
 * 1 つの貨物に何が起きたかを、時系列で出す（US15）。
 *
 * **古い順に並べる。** 荷役は起きた順に読むものであり、新しい順にすると
 * 「受領の前に積込がある」ように見える（並べ替えはサーバが行う）。
 */
export function HandlingHistoryTable({
  activities,
}: Readonly<{ activities: HandlingActivity[] }>) {
  const { data: types = [] } = useHandlingTypes()

  if (activities.length === 0) {
    return <p className="text-sm text-gray-600">まだ作業の記録はありません。</p>
  }

  // 種別の表示名はサーバが持つ（画面に対訳表を置くと、直しが 2 箇所に分かれる）
  const labelOf = (type: string) =>
    types.find((candidate) => candidate.type === type)?.label ?? type

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-gray-300 text-left">
            <th className="py-2">作業</th>
            <th>場所</th>
            <th>日時</th>
            <th>航海</th>
            <th>作業者</th>
            <th>備考</th>
          </tr>
        </thead>
        <tbody>
          {activities.map((activity) => (
            <tr key={activity.id} className="border-b">
              <td className="py-2">{labelOf(activity.type)}</td>
              <td>
                {activity.locationName}
                {/* 港は名前で、コードは併記にとどめる（表示規約） */}
                <span className="ml-1 text-gray-500">({activity.locationUnLocode})</span>
              </td>
              {/* 日時は業務タイムゾーン（表示規約） */}
              <td>{formatBusinessDateTime(activity.completionTime)}</td>
              <td>{activity.voyageNumber ?? '—'}</td>
              <td>{activity.operatorName}</td>
              <td>
                {/* 予定外だったことは記録に残る（[ADR-023] 決定 3）。
                    US28（IT10）で誤配を扱うときの入力になる */}
                {activity.offRoute && (
                  <span className="rounded bg-amber-100 px-2 py-1 text-amber-900">予定外</span>
                )}
                {activity.consigneeConfirmation !== null && (
                  <span className="ml-1 text-gray-700">
                    荷受人確認: {activity.consigneeConfirmation}
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
