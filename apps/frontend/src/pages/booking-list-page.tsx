import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useBookings } from '../features/booking/queries'
import {
  BOOKING_STATUS_LABELS,
  CARGO_TYPE_LABELS,
  type CargoType,
} from '../features/booking/types'

/**
 * 種別の見た目。危険物・冷凍は取り違えると事故になるため、一覧でも色で分かるようにする。
 */
function cargoTypeBadgeClass(type: CargoType): string {
  if (type === 'HAZARDOUS') {
    return 'rounded bg-red-100 px-2 py-1 text-red-800'
  }
  if (type === 'REFRIGERATED') {
    return 'rounded bg-sky-100 px-2 py-1 text-sky-800'
  }
  return ''
}

export function BookingListPage() {
  // 絞り込み条件を URL に持つ。登録直後に「入ったか」を確かめる導線を壊さないため
  const [searchParams, setSearchParams] = useSearchParams()
  const type = (searchParams.get('type') ?? '') as CargoType | ''
  const keyword = searchParams.get('keyword') ?? ''
  const registered = searchParams.get('registered')
  // 経路設計待ちだけを見るための絞り込み（US06）。ダッシュボードの件数からここへ来る
  const routingStatus = searchParams.get('routingStatus') ?? ''
  const [input, setInput] = useState(keyword)

  const { data, isPending } = useBookings(type, keyword, routingStatus)
  const bookings = data?.bookings ?? []

  function applyFilters(next: { type?: CargoType | ''; keyword?: string }) {
    const params = new URLSearchParams()
    const nextType = next.type ?? type
    const nextKeyword = next.keyword ?? keyword
    if (nextType !== '') {
      params.set('type', nextType)
    }
    if (nextKeyword.trim() !== '') {
      params.set('keyword', nextKeyword.trim())
    }
    // 経路設計待ちで来た人が種別を変えても、その絞り込みは外れない。
    // 外れると、経路設計者はいつのまにか担当外の予約を見ることになる
    if (routingStatus !== '') {
      params.set('routingStatus', routingStatus)
    }
    setSearchParams(params)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">
          {routingStatus === 'ROUTING_REQUESTED' ? '経路設計を待っている予約' : '貨物予約'}
        </h1>
        <Link to="/booking/new" className="rounded bg-blue-600 px-4 py-2 text-sm text-white">
          新規登録
        </Link>
      </div>

      {registered !== null && (
        <output className="block rounded border border-green-300 bg-green-50 p-4 text-gray-800">
          予約番号 <strong>{registered}</strong> を発行しました。状態は「仮受付」です。
        </output>
      )}

      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          applyFilters({ keyword: input })
        }}
      >
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-gray-700">
            貨物種別
          </label>
          <select
            id="type"
            value={type}
            onChange={(event) => applyFilters({ type: event.target.value as CargoType | '' })}
            className="mt-1 rounded border border-gray-300 px-3 py-2"
          >
            <option value="">すべて</option>
            {Object.entries(CARGO_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="keyword" className="block text-sm font-medium text-gray-700">
            予約番号または荷主の名前
          </label>
          <input
            id="keyword"
            type="search"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            className="mt-1 w-72 rounded border border-gray-300 px-3 py-2"
          />
        </div>

        <button type="submit" className="rounded border border-gray-300 px-4 py-2">
          検索
        </button>
      </form>

      {isPending && <p className="text-gray-600">読み込んでいます…</p>}

      {!isPending && data !== undefined && (
        <p className="text-sm text-gray-700">
          {data.totalCount} 件
          {/* 上限で切ったことを黙っていると「全件見た」と受け取られる */}
          {data.truncated && (
            <span className="ml-2 text-amber-700">
              （新しい {data.limit} 件のみ表示しています。絞り込んでください）
            </span>
          )}
        </p>
      )}

      {!isPending && bookings.length === 0 && (
        <p className="text-gray-600">
          条件に合う予約がありません。条件を変えるか、新しく登録してください。
        </p>
      )}

      {bookings.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-full border bg-white text-sm">
            <thead className="bg-gray-50 text-left">
              <tr>
                <th className="border-b px-4 py-2">予約番号</th>
                <th className="border-b px-4 py-2">荷主</th>
                <th className="border-b px-4 py-2">状態</th>
                <th className="border-b px-4 py-2">種別</th>
                <th className="border-b px-4 py-2">出発地</th>
                <th className="border-b px-4 py-2">目的地</th>
                <th className="border-b px-4 py-2">到着期限</th>
                <th className="border-b px-4 py-2">重量(kg)</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map((booking) => (
                <tr key={booking.id}>
                  <td className="border-b px-4 py-2">
                    {/* 予約番号から詳細へ。内容を確かめられないと、引き渡す前の点検ができない */}
                    <Link
                      to={`/booking/${booking.bookingId}`}
                      className="text-blue-600 hover:underline"
                    >
                      {booking.bookingId}
                    </Link>
                  </td>
                  <td className="border-b px-4 py-2">{booking.shipperName ?? '—'}</td>
                  <td className="border-b px-4 py-2">
                    {BOOKING_STATUS_LABELS[booking.bookingStatus] ?? booking.bookingStatus}
                  </td>
                  <td className="border-b px-4 py-2">
                    {/* 危険物・冷凍は取り違えると事故になる。一覧で分かるようにする */}
                    <span className={cargoTypeBadgeClass(booking.type)}>
                      {CARGO_TYPE_LABELS[booking.type]}
                    </span>
                  </td>
                  <td className="border-b px-4 py-2">{booking.originName}</td>
                  <td className="border-b px-4 py-2">{booking.destinationName}</td>
                  <td className="border-b px-4 py-2">{booking.arrivalDeadline}</td>
                  <td className="border-b px-4 py-2">{booking.weightKg}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
