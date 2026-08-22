import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../lib/api-client'
import { withReturnTo } from '../lib/return-path'
import { formatBusinessDateTime } from '../lib/business-time'
import { useAssignRoute, useBooking, useRequestConsultation } from '../features/booking/queries'
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

/** 積み替えの上限の既定値。サーバの既定（ADR-017）と同じ。 */
const DEFAULT_TRANSSHIPMENTS = 2

/** 日付（YYYY-MM-DD）に日数を足す。暦の計算だけなので時刻もタイムゾーンも持ち込まない。 */
function addDays(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + days))
  return shifted.toISOString().slice(0, 10)
}

function formatCost(amount: number): string {
  return `約 ${Math.round(amount / 10000).toLocaleString('ja-JP')} 万円`
}

/** 待ち時間を「1 日 14 時間」の形で表す。分のままでは長さが直感的に分からない。 */
function formatLayover(minutes: number): string {
  const days = Math.floor(minutes / (24 * 60))
  const hours = Math.floor((minutes % (24 * 60)) / 60)
  if (days === 0) {
    return `${hours} 時間`
  }
  return hours === 0 ? `${days} 日` : `${days} 日 ${hours} 時間`
}

/** 経路を「東京 →（上海）→ ロサンゼルス」の形で表す。 */
function describeRoute(candidate: RouteCandidate, originName: string, destinationName: string) {
  const via = candidate.transitPorts
    .map((port) =>
      port.layoverMinutes === null
        ? `（${port.name} / ${port.unLocode}）`
        : // どこで止まるかと、そこで何時間待つかは一続きの情報。分けて置くと読み合わせが要る
          `（${port.name} / ${port.unLocode}・待ち ${formatLayover(port.layoverMinutes)}）`,
    )
    .join(' → ')
  return via === '' ? `${originName} → ${destinationName}` : `${originName} → ${via} → ${destinationName}`
}

/**
 * 経路設計（US08）。
 *
 * 経路設計者が、引き渡された予約に対して期限内に着く経路の候補を見比べ、1 件を選んで
 * 予約に紐付ける画面（US08・US09・US10・US11）。
 *
 * 確定は予約の状態を動かし荷主への提示につながるため、**押した瞬間には確定せず確認を挟む**。
 * 予約の条件から緩めて探している間と、営業へ差し戻し中は選ばせない（サーバも断る）。
 *
 * 予約から条件を引き継いだ状態で開く。空のフォームを出すと、経路設計者は予約詳細と
 * この画面を往復して転記することになり、その過程で条件が変わる。
 */
export function RouteDesignPage() {
  const { bookingId = '' } = useParams()
  const { data: booking, isLoading: loadingBooking, isError: bookingFailed } = useBooking(bookingId)

  // 調整した条件は URL に持つ（US10）。状態に持つと、航海詳細を見て戻っただけで
  // 条件が消え、3 件比べる間に同じ条件を 3 回入れ直すことになる。再読み込みでも消えない
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  // 選んだ候補。確定するまでは予約に何も起きていない
  const [chosen, setChosen] = useState<RouteCandidate | null>(null)
  const [assignFailed, setAssignFailed] = useState<string | null>(null)
  const assign = useAssignRoute(bookingId)
  const consultation = useRequestConsultation(bookingId)
  const deadline = searchParams.get('deadline')
  const maxTransshipments = Number(searchParams.get('maxTransshipments') ?? DEFAULT_TRANSSHIPMENTS)
  const earliestDeparture = searchParams.get('earliestDeparture')

  /** 条件を 1 つ差し替える。他の条件は URL に残したままにする。 */
  function updateCriteria(
    key: 'deadline' | 'maxTransshipments' | 'earliestDeparture',
    value: string | null,
  ) {
    const next = new URLSearchParams(searchParams)
    if (value === null) {
      next.delete(key)
    } else {
      next.set(key, value)
    }
    // 条件を変えたら選択は解除する。候補は取り直されるのに選んだ候補が古いまま残ると、
    // 画面に出ていないものを確定できてしまう
    setChosen(null)
    // 条件の調整は「別のページに進む」ことではない。戻るボタンで前の条件に戻れると
    // 履歴が条件の数だけ積み上がり、予約詳細へ戻るのに何度も押すことになる
    setSearchParams(next, { replace: true })
  }

  const setDeadline = (value: string | null) => updateCriteria('deadline', value)
  const setMaxTransshipments = (value: number) =>
    updateCriteria('maxTransshipments', String(value))
  const setEarliestDeparture = (value: string) =>
    updateCriteria('earliestDeparture', value === '' ? null : value)

  const cargoType = (booking?.type ?? 'GENERAL') as RoutingCargoType
  const effectiveDeadline = deadline ?? booking?.arrivalDeadline ?? ''
  const effectiveEarliestDeparture = earliestDeparture ?? booking?.departureDate ?? ''

  /**
   * 予約の条件から動かして探しているか。
   *
   * 確定時の再検証は**予約が持つ条件**で行う（サーバは画面の条件を信じない）。したがって
   * 緩めた条件で見つけた経路は必ず断られる。しかも理由は「航海スケジュールが変わった」に
   * 見え、経路設計者は航海マスタを疑って探し回る。**押せないようにして理由を先に伝える。**
   *
   * 業務としても、到着期限と出発希望日は荷主との約束であり、経路設計者だけで
   * 確定してよいものではない。
   */
  const loosened =
    booking !== undefined
    && (effectiveDeadline !== booking.arrivalDeadline
      || effectiveEarliestDeparture !== (booking.departureDate ?? ''))

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
          // 予約の出発希望日を引き継ぐ。画面で調整したときはそちらを使う
          earliestDeparture: effectiveEarliestDeparture === '' ? null : effectiveEarliestDeparture,
        }

  const { data, isLoading, isError, error, refetch } = useRouteCandidates(criteria)

  if (loadingBooking) {
    return <p>読み込んでいます…</p>
  }
  if (bookingFailed || booking === undefined) {
    return <p role="alert">予約を読み込めませんでした。</p>
  }

  const candidates = data?.candidates ?? []
  const applied = data?.appliedCriteria

  // 差し戻し中の予約には割り当てられない（サーバも 409 で断る）。押せるようにすると、
  // 実物でだけ断られる
  const returnedToSales = booking.routingStatus === 'CONSULTATION_REQUESTED'
  const selectable = !loosened && !returnedToSales

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
          <span className="text-sm text-gray-600">出発希望日</span>
          {/* 荷主が「この日以降でないと倉庫に入らない」と言っているのに、それより前に
              出る便を候補に出すと、押さえても積むものがない */}
          <input
            type="date"
            value={effectiveEarliestDeparture}
            onChange={(event) => setEarliestDeparture(event.target.value)}
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

      {chosen !== null && (
        <section className="space-y-3 rounded border border-blue-300 bg-blue-50 p-4">
          <h2 className="font-bold">この経路で確定しますか</h2>
          <dl className="grid grid-cols-[8rem_1fr] gap-1 text-sm">
            <dt className="text-gray-600">経路</dt>
            <dd>{describeRoute(chosen, booking.originName, booking.destinationName)}</dd>
            <dt className="text-gray-600">航海</dt>
            <dd>
              {chosen.legs
                .map((leg) => `${leg.voyageNumber}（${leg.vesselName} / ${leg.carrierName}）`)
                .join(' → ')}
            </dd>
            <dt className="text-gray-600">出発</dt>
            <dd>{formatBusinessDateTime(chosen.departureTime)}</dd>
            <dt className="text-gray-600">到着</dt>
            <dd>{formatBusinessDateTime(chosen.arrivalTime)}</dd>
            <dt className="text-gray-600">輸送日数</dt>
            <dd>{chosen.transitDays} 日</dd>
            <dt className="text-gray-600">費用の概算</dt>
            <dd>{formatCost(chosen.estimatedCost)}</dd>
          </dl>
          {/* 状態の変化を先に伝える。押したあとに気づくことにしない */}
          <p className="text-sm text-gray-700">
            確定すると、予約の状態が「経路提案中」になります。費用は
            <strong>概算</strong>です。正式な料金は精算時に確定します。
          </p>

          {assignFailed !== null && (
            <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
              {assignFailed}
            </p>
          )}

          <div className="flex gap-3">
            <button
              type="button"
              disabled={assign.isPending}
              onClick={() => {
                setAssignFailed(null)
                assign.mutate(
                  {
                    legs: chosen.legs.map((leg) => ({
                      voyageNumber: leg.voyageNumber,
                      loadUnLocode: leg.fromUnLocode,
                      unloadUnLocode: leg.toUnLocode,
                      loadTime: leg.departureTime,
                      unloadTime: leg.arrivalTime,
                    })),
                    maxTransshipments,
                  },
                  {
                    // 確定できたことは、予約詳細に旅程が出ていることで分かる
                    onSuccess: () => navigate(`/booking/${booking.bookingId}`),
                    onError: (error) => {
                      // 次の行動は「もう一度探す」であり、入力の修正ではない。
                      // **候補も取り直す。**古い候補表が残ると、そこから選び直して同じ 409 になる
                      const conflict = error instanceof ApiError && error.status === 409
                      setAssignFailed(
                        error instanceof ApiError && (conflict || error.status === 503)
                          ? `${error.message}`
                          : '経路を確定できませんでした。時間をおいて再度お試しください。',
                      )
                      setChosen(null)
                      if (conflict) {
                        void refetch()
                      }
                    },
                  },
                )
              }}
              className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300"
            >
              この経路で確定する
            </button>
            <button
              type="button"
              onClick={() => setChosen(null)}
              className="rounded border border-gray-400 px-4 py-2 text-sm"
            >
              選び直す
            </button>
          </div>
        </section>
      )}

      {/* 確定に失敗して一覧へ戻したときも、理由は残す */}
      {chosen === null && assignFailed !== null && (
        <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          {assignFailed}
        </p>
      )}

      {!isLoading && !isError && candidates.length > 0 && !selectable && (
        <p
          role="alert"
          className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-gray-800"
        >
          {loosened ? (
            <>
              いまは<strong>予約の条件と違う条件</strong>で探しています。この条件で進めるには
              荷主の合意が要るため、ここからは確定できません。合意が取れたら営業に予約の条件を
              直してもらうか、
              <strong>[条件協議を依頼する]</strong> で営業へ戻してください。
            </>
          ) : (
            <>この予約は営業へ戻しています。条件が決まってから経路を確定してください。</>
          )}
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
                <th>船 / 運送会社</th>
                <th>出発</th>
                <th>到着</th>
                <th>輸送日数</th>
                <th>費用の概算</th>
                <th>選択</th>
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
                        // 条件ごと戻り先を渡す。渡さないと、航海を確かめて戻った人は
                        // どの予約を見ていたか分からない場所に出る
                        to={withReturnTo(
                          `/routing/voyages/${number}`,
                          `${location.pathname}${location.search}`,
                        )}
                        className="text-blue-700 underline"
                      >
                        {number}
                      </Link>
                    ))}
                  </td>
                  <td className="whitespace-nowrap">
                    {candidate.legs.map((leg) => (
                      <div key={`${leg.voyageNumber}-${leg.fromUnLocode}`}>
                        {leg.vesselName}
                        <span className="ml-1 text-gray-600">/ {leg.carrierName}</span>
                      </div>
                    ))}
                  </td>
                  <td>{formatBusinessDateTime(candidate.departureTime)}</td>
                  <td>{formatBusinessDateTime(candidate.arrivalTime)}</td>
                  <td>{candidate.transitDays} 日</td>
                  <td>{formatCost(candidate.estimatedCost)}</td>
                  <td>
                    {/* 押した瞬間に確定しない。経路の確定は予約の状態を動かし、
                        荷主への提示につながる。取り消す手段の無い操作を行から直接起こさない */}
                    {selectable ? (
                      <button
                        type="button"
                        onClick={() => setChosen(candidate)}
                        className="rounded bg-blue-600 px-3 py-1 text-xs text-white"
                      >
                        この経路を選ぶ
                      </button>
                    ) : (
                      <span className="text-xs text-gray-500">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="text-sm text-gray-600">
            費用は<strong>概算</strong>です。正式な料金は精算時に確定します。
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

          {/* 「見つかりませんでした」で終わらせない。この画面の中で行き止まりにすると、
              荷主との条件交渉が始まらない（ADR-020 決定 7） */}
          {booking.routingStatus === 'ROUTING_REQUESTED' && (
            <div className="space-y-2 border-t border-amber-300 pt-3">
              <p className="text-sm">
                条件そのものを見直す必要がありそうなら、営業へ戻して荷主と協議してもらいます。
              </p>
              <button
                type="button"
                disabled={consultation.isPending}
                onClick={() => consultation.mutate()}
                className="rounded border border-gray-400 px-3 py-1 disabled:text-gray-400"
              >
                条件協議を依頼する
              </button>
              {/* 押した本人に効いたことを知らせる。件数の再取得を待つ形だと、
                  押せたのかどうかが分からない */}
              {consultation.isSuccess && (
                <output className="block rounded border border-green-300 bg-green-50 p-2 text-sm">
                  営業へ戻しました。営業担当者のダッシュボードに表示されます。
                </output>
              )}
            </div>
          )}
          {booking.routingStatus === 'CONSULTATION_REQUESTED' && (
            <p className="text-sm text-gray-700">
              この予約は営業へ戻しています。条件が決まったら、もう一度この画面で経路を探します。
            </p>
          )}
        </div>
      )}
    </section>
  )
}
