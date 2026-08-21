import { Link, useParams } from 'react-router-dom'
import { formatBusinessDateTime } from '../lib/business-time'
import { useVoyage } from '../features/routing/queries'
import { ROUTING_CARGO_TYPE_LABELS } from '../features/routing/types'

/**
 * 航海スケジュールの詳細（#552）。
 *
 * 一覧は出発地と目的地しか見せないため、途中の寄港地と区間ごとの時刻が分からない。
 * 経路候補に出た航海が本当に使えるか（どこに寄るか・積み替えの港にいつ着くか）を
 * 確かめられないと、経路設計者は候補の妥当性を判断できない。
 */
export function VoyageDetailPage() {
  const { voyageNumber = '' } = useParams()
  const { data: voyage, isLoading, isError } = useVoyage(voyageNumber)

  if (isLoading) {
    return <p>読み込んでいます…</p>
  }
  if (isError || voyage === undefined) {
    return <p role="alert">指定された航海が見つかりません。</p>
  }

  return (
    <section className="space-y-6">
      <header className="flex items-baseline justify-between">
        <h1 className="text-xl font-bold">航海 {voyage.voyageNumber}</h1>
        <Link to="/routing/voyages" className="text-blue-700 underline">
          航海スケジュール一覧に戻る
        </Link>
      </header>

      <dl className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4">
        <div>
          <dt className="text-sm text-gray-600">船名</dt>
          <dd>{voyage.vesselName}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">運送会社</dt>
          <dd>{voyage.carrierName}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">運べる貨物</dt>
          <dd>
            {voyage.supportedCargoTypes
              .map((type) => ROUTING_CARGO_TYPE_LABELS[type])
              .join('・')}
          </dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">全体</dt>
          <dd>
            {voyage.originName} → {voyage.destinationName}
          </dd>
        </div>
      </dl>

      <div className="space-y-2">
        <h2 className="font-bold">寄港と区間（{voyage.movements.length} 区間）</h2>
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-gray-300 text-left">
              <th className="py-2">区間</th>
              <th>出発</th>
              <th>出発日時</th>
              <th>到着</th>
              <th>到着日時</th>
            </tr>
          </thead>
          <tbody>
            {voyage.movements.map((movement, index) => (
              <tr key={`${movement.departureUnLocode}-${index}`} className="border-b border-gray-200">
                <td className="py-2">{index + 1}</td>
                <td>
                  {movement.departureName}（{movement.departureUnLocode}）
                </td>
                <td>{formatBusinessDateTime(movement.departureTime)}</td>
                <td>
                  {movement.arrivalName}（{movement.arrivalUnLocode}）
                </td>
                <td>{formatBusinessDateTime(movement.arrivalTime)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="text-sm text-gray-600">日時は日本時間で表示しています。</p>
      </div>

      <Link
        to={`/routing/voyages/new?voyageNumber=${encodeURIComponent(voyage.voyageNumber)}`}
        className="inline-block rounded border border-gray-400 px-3 py-1"
      >
        この航海を更新する
      </Link>
    </section>
  )
}
