import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../lib/api-client'
import { formatBusinessDateTime } from '../lib/business-time'
import { useBooking } from '../features/booking/queries'
import { useRouteCandidates } from '../features/routing/queries'
import {
  ROUTING_CARGO_TYPE_LABELS,
  type RouteCandidate,
  type RouteSearchCriteria,
  type RoutingCargoType,
} from '../features/routing/types'

/** 積み替えを緩めるときの上限。既定は 2 回まで（ADR-018）。 */
const LOOSER_TRANSSHIPMENTS = 3

/** 期限を緩めるときに延ばす日数。 */
const EXTENSION_DAYS = 7

/**
 * 貨物種別のうち、運べる船が限られるもの。
 *
 * 候補が無かったとき、これが効いていることに気づけないと、経路設計者は期限だけを
 * 緩め続ける。
 */
const LIMITED_CARGO_TYPES: RoutingCargoType[] = ['HAZARDOUS', 'REFRIGERATED']

/** 日付（YYYY-MM-DD）に日数を足す。暦の計算だけなので時刻もタイムゾーンも持ち込まない。 */
function addDays(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + days))
  return shifted.toISOString().slice(0, 10)
}

function formatCost(amount: number): string {
  return `約 ${Math.round(amount / 10000).toLocaleString('ja-JP')} 万円`
}

/** 経路を「東京 →（上海）→ ロサンゼルス」の形で表す。 */
function describeRoute(candidate: RouteCandidate, originName: string, destinationName: string) {
  const via = candidate.transitPorts.map((port) => `（${port.name} / ${port.unLocode}）`).join(' → ')
  return via === '' ? `${originName} → ${destinationName}` : `${originName} → ${via} → ${destinationName}`
}

/**
 * 経路設計（US08）。
 *
 * 経路設計者が、引き渡された予約に対して期限内に着く経路の候補を見比べる画面。
 * **IT4 は一覧の表示まで**で、選択・確定は US09（IT5）で足す。押せない [選択] は置かない。
 *
 * 予約から条件を引き継いだ状態で開く。空のフォームを出すと、経路設計者は予約詳細と
 * この画面を往復して転記することになり、その過程で条件が変わる。
 */
export function RouteDesignPage() {
  const { bookingId = '' } = useParams()
  const { data: booking, isLoading: loadingBooking, isError: bookingFailed } = useBooking(bookingId)

  const [deadline, setDeadline] = useState<string | null>(null)
  const [maxTransshipments, setMaxTransshipments] = useState(2)

  const cargoType = (booking?.type ?? 'GENERAL') as RoutingCargoType
  const effectiveDeadline = deadline ?? booking?.arrivalDeadline ?? ''

  // 期限が空のまま問い合わせると 400 になり、画面には「算出できませんでした」だけが出る。
  // 経路設計者は何もしていないのに失敗を見ることになる
  const criteria: RouteSearchCriteria | null =
    booking === undefined || effectiveDeadline === ''
      ? null
      : {
          origin: booking.originUnLocode,
          destination: booking.destinationUnLocode,
          // 期限は日付のまま送る。日時への変換はサーバが業務タイムゾーンで行う（ADR-017）
          deadline: effectiveDeadline,
          cargoType,
          maxTransshipments,
        }

  const { data, isLoading, isError, error } = useRouteCandidates(criteria)

  if (loadingBooking) {
    return <p>読み込んでいます…</p>
  }
  if (bookingFailed || booking === undefined) {
    return <p role="alert">予約を読み込めませんでした。</p>
  }

  const candidates = data?.candidates ?? []
  const applied = data?.appliedCriteria

  return (
    <section className="space-y-6">
      <header className="flex items-baseline justify-between">
        <h1 className="text-xl font-bold">経路設計</h1>
        <div className="space-x-4">
          <Link to={`/booking/${booking.bookingId}`} className="text-blue-700 underline">
            予約詳細に戻る
          </Link>
          {/* 朝の仕事は 1 件ではなく待ち行列を上から片づけること。
              毎回ダッシュボードへ戻らせない */}
          <Link
            to="/booking?routingStatus=ROUTING_REQUESTED"
            className="text-blue-700 underline"
          >
            経路設計待ちの一覧に戻る
          </Link>
        </div>
      </header>

      <dl className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4">
        <div>
          <dt className="text-sm text-gray-600">予約番号</dt>
          <dd>{booking.bookingId}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">荷主</dt>
          <dd>{booking.shipperName ?? '―'}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">出発地</dt>
          <dd>
            {booking.originName}（{booking.originUnLocode}）
          </dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">目的地</dt>
          <dd>
            {booking.destinationName}（{booking.destinationUnLocode}）
          </dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">貨物</dt>
          <dd>{ROUTING_CARGO_TYPE_LABELS[cargoType]}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">重量</dt>
          <dd>{booking.weightKg.toLocaleString('ja-JP')} kg</dd>
        </div>
      </dl>

      {deadline !== null && deadline !== booking.arrivalDeadline && (
        <p className="rounded border border-amber-300 bg-amber-50 p-3 text-sm">
          この予約の到着期限は <strong>{booking.arrivalDeadline}</strong> です。いま{' '}
          <strong>{effectiveDeadline}</strong> で探しています。
          <strong>この条件で進めるには荷主の合意が要ります。</strong>{' '}
          <button
            type="button"
            onClick={() => setDeadline(null)}
            className="underline"
          >
            予約の期限に戻す
          </button>
        </p>
      )}

      <form className="flex flex-wrap items-end gap-4 rounded border border-gray-200 p-4">
        <label className="flex flex-col">
          <span className="text-sm text-gray-600">到着期限</span>
          <input
            type="date"
            value={effectiveDeadline}
            onChange={(event) => setDeadline(event.target.value)}
            className="rounded border border-gray-300 px-2 py-1"
          />
        </label>
        <label className="flex flex-col">
          <span className="text-sm text-gray-600">積み替えの上限</span>
          <select
            value={maxTransshipments}
            onChange={(event) => setMaxTransshipments(Number(event.target.value))}
            className="rounded border border-gray-300 px-2 py-1"
          >
            <option value={0}>直行便のみ</option>
            <option value={1}>1 回まで</option>
            <option value={2}>2 回まで</option>
            <option value={3}>3 回まで</option>
          </select>
        </label>
      </form>

      {effectiveDeadline === '' && (
        <p role="alert" className="rounded border border-amber-300 bg-amber-50 p-3">
          到着期限を入力してください。
        </p>
      )}

      {isLoading && <p>経路を探しています…</p>}
      {/* サーバが区別した理由をそのまま見せる。「経路が無い」と「港の指定が誤り」を
          同じ文言にすると、経路設計者は通信のせいだと思って何度も開き直す */}
      {isError && (
        <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          {error instanceof ApiError
            ? error.message
            : '経路候補を算出できませんでした。時間をおいて開き直してください。'}
        </p>
      )}

      {!isLoading && !isError && candidates.length > 0 && (
        <div className="space-y-2">
          <h2 className="font-bold">候補 {data?.totalCount} 件（推奨順）</h2>
          <p className="text-sm text-gray-600">
            直行便を最優先に並べています。到着の早さだけで並べているわけではありません。
          </p>
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 text-left">
                <th className="py-2">順位</th>
                <th>経路</th>
                <th>航海</th>
                <th>出発</th>
                <th>到着</th>
                <th>輸送日数</th>
                <th>費用の概算</th>
              </tr>
            </thead>
            <tbody>
              {candidates.map((candidate) => (
                <tr key={candidate.rank} className="border-b border-gray-200">
                  <td className="py-2">
                    {candidate.rank}
                    {candidate.direct && (
                      <span className="ml-1 rounded bg-green-100 px-1 text-xs text-green-800">
                        直行
                      </span>
                    )}
                  </td>
                  <td>
                    {describeRoute(candidate, booking.originName, booking.destinationName)}
                  </td>
                  <td className="space-x-1">
                    {candidate.voyageNumbers.map((number) => (
                      <Link
                        key={number}
                        to={`/routing/voyages/${number}`}
                        className="text-blue-700 underline"
                      >
                        {number}
                      </Link>
                    ))}
                  </td>
                  <td>{formatBusinessDateTime(candidate.departureTime)}</td>
                  <td>{formatBusinessDateTime(candidate.arrivalTime)}</td>
                  <td>{candidate.transitDays} 日</td>
                  <td>{formatCost(candidate.estimatedCost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="text-sm text-gray-600">
            費用は<strong>概算</strong>です。正式な料金は精算時に確定します。
          </p>
          <p className="text-sm text-gray-600">
            経路を選んで予約に紐付ける操作は、次のリリースで使えるようになります。
          </p>
        </div>
      )}

      {!isLoading && !isError && candidates.length === 0 && (
        <div className="space-y-3 rounded border border-amber-300 bg-amber-50 p-4">
          <h2 className="font-bold">期限内に到着できる経路が見つかりませんでした</h2>
          <div className="text-sm">
            <p>いま使った条件</p>
            <ul className="list-disc pl-5">
              <li>到着期限 {effectiveDeadline} まで</li>
              <li>
                貨物種別 {ROUTING_CARGO_TYPE_LABELS[applied?.cargoType ?? cargoType]}
                {LIMITED_CARGO_TYPES.includes(applied?.cargoType ?? cargoType) && (
                  <span>（運べる船が限られます）</span>
                )}
              </li>
              <li>積み替え {applied?.maxTransshipments ?? maxTransshipments} 回まで</li>
            </ul>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => setDeadline(addDays(effectiveDeadline, EXTENSION_DAYS))}
              className="rounded border border-gray-400 px-3 py-1"
            >
              到着期限を 1 週間延ばす
            </button>
            <button
              type="button"
              onClick={() => setMaxTransshipments(LOOSER_TRANSSHIPMENTS)}
              className="rounded border border-gray-400 px-3 py-1"
              disabled={maxTransshipments >= LOOSER_TRANSSHIPMENTS}
            >
              積み替えを {LOOSER_TRANSSHIPMENTS} 回まで許す
            </button>
          </div>
          <p className="text-sm">
            それでも見つからない場合は、航海スケジュールにその区間の便が登録されているかを
            確認してください。
          </p>
          <Link to="/routing/voyages" className="text-blue-700 underline">
            航海スケジュールを見る
          </Link>
        </div>
      )}
    </section>
  )
}
