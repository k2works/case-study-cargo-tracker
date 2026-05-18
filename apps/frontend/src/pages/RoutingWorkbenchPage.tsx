import { useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router'
import { useBookings } from '../features/booking/hooks/useBookings'
import { fetchRouteCandidates, adjustRouteCandidates, selectRoute, useAssignRoute } from '../features/routing/workbench/hooks/useWorkbench'
import { RouteCandidateTable } from '../features/routing/workbench/components/RouteCandidateTable'
import type { CandidateItem } from '../features/routing/workbench/types/workbench'

// S14 経路設計ワークベンチ（US08/US09/US10/US11）
export function RoutingWorkbenchPage() {
  const { bookingId = '' } = useParams<{ bookingId: string }>()
  const navigate = useNavigate()
  const { data: bookings, isLoading, isError } = useBookings()
  const assignRoute = useAssignRoute(bookingId)

  const [candidates, setCandidates] = useState<CandidateItem[] | null>(null)
  const [isSearching, setIsSearching] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [noRoutesMessage, setNoRoutesMessage] = useState<string | null>(null)
  const [excludePorts, setExcludePorts] = useState('')
  const [isAdjusting, setIsAdjusting] = useState(false)

  const booking = bookings?.find((b) => b.bookingId === bookingId)

  if (isLoading) return <p className="p-6 text-gray-500">読み込み中...</p>
  if (isError) return <p className="p-6 text-red-600">データの取得に失敗しました。</p>
  if (!booking) {
    return (
      <div className="p-6">
        <p className="text-red-600">予約が見つかりません: {bookingId}</p>
        <Link to="/bookings" className="text-blue-600 underline">
          予約一覧に戻る
        </Link>
      </div>
    )
  }

  const handleSearch = async () => {
    setIsSearching(true)
    setMessage(null)
    setErrorMessage(null)
    setNoRoutesMessage(null)
    try {
      const res = await fetchRouteCandidates(
        booking.originUnLocode,
        booking.destinationUnLocode,
        booking.arrivalDeadline,
        booking.cargoType,
      )
      setCandidates(res.candidates)
    } catch {
      setErrorMessage('経路候補の取得に失敗しました。')
    } finally {
      setIsSearching(false)
    }
  }

  const handleAdjust = async () => {
    setIsAdjusting(true)
    setNoRoutesMessage(null)
    setErrorMessage(null)
    const excludeList = excludePorts
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
    try {
      const res = await adjustRouteCandidates({
        origin: booking.originUnLocode,
        destination: booking.destinationUnLocode,
        arrivalDeadline: booking.arrivalDeadline,
        cargoType: booking.cargoType,
        excludePorts: excludeList,
      })
      setCandidates(res.candidates)
      if (res.noRoutesMessage) setNoRoutesMessage(res.noRoutesMessage)
    } catch {
      setErrorMessage('条件調整後の再算出に失敗しました。')
    } finally {
      setIsAdjusting(false)
    }
  }

  const handleSelect = async (index: number) => {
    setMessage(null)
    setErrorMessage(null)
    try {
      const selected = await selectRoute({
        origin: booking.originUnLocode,
        destination: booking.destinationUnLocode,
        arrivalDeadline: booking.arrivalDeadline,
        cargoType: booking.cargoType,
        selectedIndex: index,
      })
      assignRoute.mutate(
        { legs: selected.legs },
        {
          onSuccess: () => {
            setMessage('経路を予約に紐付けました。予約詳細に戻ります...')
            setTimeout(() => navigate(`/bookings/${bookingId}`), 1500)
          },
          onError: () => {
            setErrorMessage('経路の紐付けに失敗しました。')
          },
        },
      )
    } catch {
      setErrorMessage('経路の選択に失敗しました。')
    }
  }

  return (
    <div className="p-6 max-w-4xl">
      <div className="mb-4 flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">経路設計ワークベンチ</h1>
        <Link to={`/bookings/${bookingId}`} className="text-sm text-blue-600 underline">
          ← 予約詳細に戻る
        </Link>
      </div>

      {message && (
        <div
          data-testid="assign-success"
          className="mb-4 rounded border border-green-200 bg-green-50 px-4 py-2 text-sm text-green-800"
        >
          {message}
        </div>
      )}
      {errorMessage && (
        <div
          data-testid="assign-error"
          className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-800"
        >
          {errorMessage}
        </div>
      )}

      <section className="mb-6 rounded border border-gray-200 bg-white p-4">
        <h2 className="mb-2 text-sm font-semibold text-gray-700">予約情報</h2>
        <dl className="grid grid-cols-3 gap-x-4 gap-y-1 text-sm" data-testid="booking-info">
          <dt className="text-gray-500">予約 ID</dt>
          <dd className="col-span-2 font-mono">{booking.bookingId}</dd>
          <dt className="text-gray-500">出発地</dt>
          <dd className="col-span-2 font-mono">{booking.originUnLocode}</dd>
          <dt className="text-gray-500">目的地</dt>
          <dd className="col-span-2 font-mono">{booking.destinationUnLocode}</dd>
          <dt className="text-gray-500">到着期限</dt>
          <dd className="col-span-2">{booking.arrivalDeadline}</dd>
          <dt className="text-gray-500">貨物種別</dt>
          <dd className="col-span-2">{booking.cargoType}</dd>
          <dt className="text-gray-500">状態</dt>
          <dd className="col-span-2">
            <span className="inline-block px-2 py-0.5 rounded text-xs bg-blue-100 text-blue-800">
              {booking.bookingStatus}
            </span>
          </dd>
        </dl>
      </section>

      <section className="mb-6">
        <button
          type="button"
          data-testid="search-candidates-button"
          onClick={handleSearch}
          disabled={isSearching}
          className="rounded bg-green-600 px-4 py-2 text-sm text-white hover:bg-green-700 disabled:opacity-50"
        >
          {isSearching ? '候補を算出中...' : '経路候補を算出'}
        </button>
        <p className="mt-1 text-xs text-gray-500">
          出発地・目的地・期限・貨物種別を条件に経路候補を算出します。
        </p>
      </section>

      {candidates !== null && candidates.length === 0 && (
        <section
          data-testid="adjust-conditions-form"
          className="mb-6 rounded border border-yellow-200 bg-yellow-50 p-4"
        >
          <h2 className="mb-2 text-sm font-semibold text-yellow-800">
            条件に合う経路候補が見つかりませんでした。条件を調整してください。
          </h2>
          <div className="mb-3">
            <label className="block text-xs text-gray-600 mb-1" htmlFor="exclude-ports">
              除外する経由地（UN/LOCODE、カンマ区切り）
            </label>
            <input
              id="exclude-ports"
              type="text"
              value={excludePorts}
              onChange={(e) => setExcludePorts(e.target.value)}
              placeholder="例: TWKHH, SGSIN"
              className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm"
            />
          </div>
          <button
            type="button"
            data-testid="adjust-search-button"
            onClick={handleAdjust}
            disabled={isAdjusting}
            className="rounded bg-yellow-600 px-4 py-2 text-sm text-white hover:bg-yellow-700 disabled:opacity-50"
          >
            {isAdjusting ? '再算出中...' : '条件を調整して再算出'}
          </button>
          {noRoutesMessage && (
            <p
              data-testid="no-routes-message"
              className="mt-3 text-sm text-red-700"
            >
              {noRoutesMessage}
            </p>
          )}
        </section>
      )}

      {candidates !== null && candidates.length > 0 && (
        <section data-testid="candidates-section">
          <h2 className="mb-3 text-sm font-semibold text-gray-700">
            経路候補一覧（{candidates.length} 件）
          </h2>
          <RouteCandidateTable
            candidates={candidates}
            onSelect={handleSelect}
            isSubmitting={assignRoute.isPending}
          />
        </section>
      )}
    </div>
  )
}
