import { useState, useEffect } from 'react'
import { useParams, useNavigate, Navigate } from 'react-router'
import { useItineraries } from '../features/routing/hooks/useItineraries'
import { useAssignRoute } from '../features/booking/hooks/useBookings'
import type { Itinerary } from '../features/routing/types/itinerary'

const DEFAULT_ORIGIN = 'JPTYO'
const DEFAULT_DESTINATION = 'CNSHA'

export function RoutingDesignPage() {
  const { bookingId } = useParams<{ bookingId: string }>()
  const navigate = useNavigate()

  const [originUnlocode, setOriginUnlocode] = useState(DEFAULT_ORIGIN)
  const [destinationUnlocode, setDestinationUnlocode] = useState(DEFAULT_DESTINATION)
  const [arrivalDeadline, setArrivalDeadline] = useState('')
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const { mutate: searchItineraries, data: itineraries, isPending: isSearching, isError: isSearchError } = useItineraries()
  const { mutate: assignRoute, isPending: isAssigning } = useAssignRoute(bookingId ?? '')

  if (!bookingId) {
    return <Navigate to="/routing/assignments" replace />
  }

  useEffect(() => {
    searchItineraries({
      originUnlocode: DEFAULT_ORIGIN,
      destinationUnlocode: DEFAULT_DESTINATION,
      arrivalDeadline: null,
    })
  }, [])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setSelectedIndex(null)
    setSuccessMessage(null)
    setErrorMessage(null)
    searchItineraries({
      originUnlocode,
      destinationUnlocode,
      arrivalDeadline: arrivalDeadline || null,
    })
  }

  const handleAssign = () => {
    if (selectedIndex === null || !itineraries || !bookingId) return
    const selected = itineraries[selectedIndex]
    assignRoute(
      { legs: selected.legs },
      {
        onSuccess: () => {
          navigate(`/bookings/${bookingId}`)
        },
        onError: () => {
          setErrorMessage('経路の割り当てに失敗しました。')
        },
      }
    )
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          経路設計{bookingId ? ` — ${bookingId}` : ''}
        </h1>
      </div>

      {successMessage && (
        <div className="mb-4 rounded-md bg-green-50 border border-green-200 p-3 text-sm text-green-800">
          {successMessage}
        </div>
      )}
      {errorMessage && (
        <div className="mb-4 rounded-md bg-red-50 border border-red-200 p-3 text-sm text-red-800">
          {errorMessage}
        </div>
      )}

      <form onSubmit={handleSearch} className="bg-white border border-gray-200 rounded-lg p-4 mb-6 flex items-end gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">出発地 (UNLOCODE)</label>
          <input
            value={originUnlocode}
            onChange={(e) => setOriginUnlocode(e.target.value.toUpperCase())}
            required
            placeholder="JPTYO"
            maxLength={5}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm w-28 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">到着地 (UNLOCODE)</label>
          <input
            value={destinationUnlocode}
            onChange={(e) => setDestinationUnlocode(e.target.value.toUpperCase())}
            required
            placeholder="CNSHA"
            maxLength={5}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm w-28 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">到着期限</label>
          <input
            type="date"
            value={arrivalDeadline}
            onChange={(e) => setArrivalDeadline(e.target.value)}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <button
          type="submit"
          disabled={isSearching}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          経路を検索
        </button>
      </form>

      {isSearching && <p className="py-4 text-center text-gray-500">検索中...</p>}
      {isSearchError && <p className="py-4 text-center text-red-500">経路の取得に失敗しました。</p>}

      {itineraries && itineraries.length === 0 && (
        <p className="py-8 text-center text-gray-500">該当する経路が見つかりませんでした。</p>
      )}

      {itineraries && itineraries.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-800">経路候補 ({itineraries.length} 件)</h2>
          {itineraries.map((itinerary: Itinerary, index: number) => (
            <div
              key={index}
              onClick={() => setSelectedIndex(index)}
              className={`border rounded-lg p-4 cursor-pointer transition-colors ${
                selectedIndex === index
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-gray-200 bg-white hover:bg-gray-50'
              }`}
            >
              <div className="flex items-center justify-between mb-3">
                <span className="text-sm font-semibold text-gray-800">
                  経路 {index + 1}（{itinerary.legs.length === 1 ? '直行便' : `${itinerary.legs.length} 区間`}）
                </span>
                {selectedIndex === index && (
                  <span className="text-xs font-medium text-blue-600 bg-blue-100 px-2 py-0.5 rounded-full">選択中</span>
                )}
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200 border border-gray-200 rounded">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">航海番号</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">積載港</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">荷卸港</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">積載日時</th>
                      <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600">荷卸日時</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {itinerary.legs.map((leg, legIndex) => (
                      <tr key={legIndex}>
                        <td className="px-3 py-2 text-sm font-mono text-gray-900">{leg.voyageNumber}</td>
                        <td className="px-3 py-2 text-sm text-gray-700">{leg.loadLocationUnlocode}</td>
                        <td className="px-3 py-2 text-sm text-gray-700">{leg.unloadLocationUnlocode}</td>
                        <td className="px-3 py-2 text-sm text-gray-700">
                          {new Date(leg.loadTime).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
                        </td>
                        <td className="px-3 py-2 text-sm text-gray-700">
                          {new Date(leg.unloadTime).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}

          {bookingId && selectedIndex !== null && (
            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={() => navigate(`/bookings/${bookingId}`)}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                キャンセル
              </button>
              <button
                onClick={handleAssign}
                disabled={isAssigning}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {isAssigning ? '割り当て中...' : 'この経路を割り当てる'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
