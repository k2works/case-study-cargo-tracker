import { useState } from 'react'
import { Link } from 'react-router'
import { useVoyages, type VoyageSearchCriteria } from '../hooks/useVoyages'

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

export function VoyageList() {
  const [criteria, setCriteria] = useState<VoyageSearchCriteria | undefined>(undefined)
  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [cargoType, setCargoType] = useState('')

  const { data: voyages, isLoading, isError } = useVoyages(criteria)

  function handleSearch() {
    const next: VoyageSearchCriteria = {}
    if (origin) next.origin = origin
    if (destination) next.destination = destination
    if (cargoType) next.cargoType = cargoType
    setCriteria(Object.keys(next).length > 0 ? next : undefined)
  }

  if (isLoading) return <p className="text-gray-500">読み込み中...</p>
  if (isError) return <p className="text-red-600">データの取得に失敗しました。</p>

  return (
    <div className="space-y-4">
      <div className="flex gap-2 items-end flex-wrap">
        <input
          type="text"
          placeholder="出発港 (JPYOK)"
          value={origin}
          onChange={(e) => setOrigin(e.target.value)}
          className="border border-gray-300 rounded px-3 py-2 text-sm w-36"
        />
        <input
          type="text"
          placeholder="到着港 (USLAX)"
          value={destination}
          onChange={(e) => setDestination(e.target.value)}
          className="border border-gray-300 rounded px-3 py-2 text-sm w-36"
        />
        <select
          value={cargoType}
          onChange={(e) => setCargoType(e.target.value)}
          className="border border-gray-300 rounded px-3 py-2 text-sm"
        >
          <option value="">貨物種別（全て）</option>
          <option value="GENERAL">GENERAL</option>
          <option value="REFRIGERATED">REFRIGERATED</option>
          <option value="HAZARDOUS">HAZARDOUS</option>
        </select>
        <button
          onClick={handleSearch}
          className="bg-blue-600 text-white px-4 py-2 rounded text-sm hover:bg-blue-700"
        >
          検索
        </button>
      </div>

    {(!voyages || voyages.length === 0) ? (
      <p className="text-gray-500">航海スケジュールが登録されていません。</p>
    ) : (
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
    )}
    </div>
  )
}
