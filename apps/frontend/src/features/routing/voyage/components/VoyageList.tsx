import { Link } from 'react-router'
import { useVoyages } from '../hooks/useVoyages'

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

export function VoyageList() {
  const { data: voyages, isLoading, isError } = useVoyages()

  if (isLoading) return <p className="text-gray-500">読み込み中...</p>
  if (isError) return <p className="text-red-600">データの取得に失敗しました。</p>

  if (!voyages || voyages.length === 0) {
    return <p className="text-gray-500">航海スケジュールが登録されていません。</p>
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-gray-700">航海番号</th>
            <th className="px-4 py-3 text-left text-gray-700">船名</th>
            <th className="px-4 py-3 text-left text-gray-700">運送会社</th>
            <th className="px-4 py-3 text-left text-gray-700">出発港</th>
            <th className="px-4 py-3 text-left text-gray-700">到着港</th>
            <th className="px-4 py-3 text-left text-gray-700">出発日時</th>
            <th className="px-4 py-3 text-left text-gray-700">到着日時</th>
            <th className="px-4 py-3 text-left text-gray-700">状態</th>
            <th className="px-4 py-3 text-left text-gray-700">操作</th>
          </tr>
        </thead>
        <tbody>
          {voyages.map((voyage) => (
            <tr key={voyage.voyageNumber} className="border-t border-gray-200 hover:bg-gray-50">
              <td className="px-4 py-3 font-mono">{voyage.voyageNumber}</td>
              <td className="px-4 py-3">{voyage.shipName}</td>
              <td className="px-4 py-3">
                {voyage.carrierName} <span className="text-xs text-gray-500">({voyage.carrierCode})</span>
              </td>
              <td className="px-4 py-3 font-mono">{voyage.originUnLocode}</td>
              <td className="px-4 py-3 font-mono">{voyage.destinationUnLocode}</td>
              <td className="px-4 py-3">{formatDateTime(voyage.departureDate)}</td>
              <td className="px-4 py-3">{formatDateTime(voyage.arrivalDate)}</td>
              <td className="px-4 py-3">
                <span className="inline-block px-2 py-0.5 rounded text-xs bg-green-100 text-green-800">
                  {voyage.status}
                </span>
              </td>
              <td className="px-4 py-3">
                <Link
                  to={`/routing/voyages/${voyage.voyageNumber}/edit`}
                  className="text-sm text-blue-600 underline"
                  data-testid={`edit-link-${voyage.voyageNumber}`}
                >
                  編集
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
