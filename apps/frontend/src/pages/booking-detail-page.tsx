import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../lib/api-client'
import { useAuthStore } from '../stores/auth-store'
import { useBooking, useRequestRouting } from '../features/booking/queries'
import {
  BOOKING_STATUS_LABELS,
  CARGO_TYPE_LABELS,
  ROUTING_STATUS_LABELS,
} from '../features/booking/types'
import { formatBusinessDateTime } from '../lib/business-time'

/**
 * 予約の詳細（US06）。
 *
 * 営業担当者が引き渡す前に内容を確かめ、経路設計者が受け取った予約の中身を見る画面。
 * 中身が見えないまま引き渡すと、経路設計者は不備に気づけないまま経路を組むことになる。
 */
export function BookingDetailPage() {
  const { bookingId = '' } = useParams()
  const { data: booking, isLoading, isError } = useBooking(bookingId)
  const request = useRequestRouting(bookingId)
  // 本番と同じ判定を使う。ここで独自に書くと、検査だけが正しく本番の誤りを素通りさせる
  const isSales = useAuthStore((state) => state.hasAnyRole(['ROLE_SALES']))
  const isRoutingPlanner = useAuthStore((state) => state.hasAnyRole(['ROLE_ROUTING']))

  function requestFailureMessage(): string | null {
    if (request.error === null || request.error === undefined) {
      return null
    }
    // 409 は入力の誤りではない。予約の状態がその操作を許さないという返事である
    if (request.error instanceof ApiError && request.error.status === 409) {
      const body = request.error.body as { message?: string } | undefined
      return body?.message ?? 'この予約は経路設計を依頼できません。'
    }
    return '経路設計を依頼できませんでした。時間をおいて再度お試しください。'
  }

  if (isLoading) {
    return <p className="text-gray-600">読み込んでいます…</p>
  }

  if (isError || booking === undefined) {
    return (
      <div className="space-y-4">
        <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          予約を表示できませんでした。予約番号を確かめてください。
        </p>
        <Link to="/booking" className="text-blue-600 hover:underline">
          貨物予約の一覧に戻る
        </Link>
      </div>
    )
  }

  const failure = requestFailureMessage()

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">予約 {booking.bookingId}</h1>
        <Link to="/booking" className="text-blue-600 hover:underline">
          一覧に戻る
        </Link>
      </div>

      {request.isSuccess && (
        <p className="rounded border border-green-200 bg-green-50 p-3 text-green-800">
          経路設計を依頼しました。経路設計者の一覧に表示されます。
        </p>
      )}

      {failure !== null && (
        <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          {failure}
        </p>
      )}

      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-gray-900">予約の状態</h2>
        <table className="w-full border-collapse text-sm">
          <tbody>
            <tr className="border-b border-gray-200">
              <th className="w-48 px-3 py-2 text-left">予約</th>
              <td className="px-3 py-2">
                {BOOKING_STATUS_LABELS[booking.bookingStatus] ?? booking.bookingStatus}
              </td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">経路</th>
              <td className="px-3 py-2">
                {ROUTING_STATUS_LABELS[booking.routingStatus] ?? booking.routingStatus}
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-gray-900">輸送の条件</h2>
        <table className="w-full border-collapse text-sm">
          <tbody>
            <tr className="border-b border-gray-200">
              <th className="w-48 px-3 py-2 text-left">荷主</th>
              <td className="px-3 py-2">{booking.shipperName ?? '（不明）'}</td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">出発地</th>
              <td className="px-3 py-2">
                {booking.originName}（{booking.originUnLocode}）
              </td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">目的地</th>
              <td className="px-3 py-2">
                {booking.destinationName}（{booking.destinationUnLocode}）
              </td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">到着期限</th>
              <td className="px-3 py-2">{booking.arrivalDeadline}</td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">出発希望日</th>
              <td className="px-3 py-2">{booking.departureDate ?? '（指定なし）'}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-gray-900">貨物の仕様</h2>
        <table className="w-full border-collapse text-sm">
          <tbody>
            <tr className="border-b border-gray-200">
              <th className="w-48 px-3 py-2 text-left">種別</th>
              <td className="px-3 py-2">{CARGO_TYPE_LABELS[booking.type]}</td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">重量</th>
              <td className="px-3 py-2">{booking.weightKg} kg</td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">個数</th>
              <td className="px-3 py-2">{booking.quantity ?? '（指定なし）'}</td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">品名</th>
              <td className="px-3 py-2">{booking.description ?? '（指定なし）'}</td>
            </tr>
            {booking.hazardousClass !== null && (
              <>
                <tr className="border-b border-gray-200">
                  <th className="px-3 py-2 text-left">危険物クラス</th>
                  <td className="px-3 py-2">{booking.hazardousClass}</td>
                </tr>
                <tr className="border-b border-gray-200">
                  <th className="px-3 py-2 text-left">UN 番号</th>
                  <td className="px-3 py-2">{booking.unNumber}</td>
                </tr>
                <tr className="border-b border-gray-200">
                  <th className="px-3 py-2 text-left">正式品名</th>
                  <td className="px-3 py-2">{booking.properShippingName}</td>
                </tr>
              </>
            )}
            {booking.minCelsius !== null && (
              <tr className="border-b border-gray-200">
                <th className="px-3 py-2 text-left">保管温度</th>
                <td className="px-3 py-2">
                  {booking.minCelsius}℃ 〜 {booking.maxCelsius}℃
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      {/* 引き渡しは営業担当者の操作。経路設計者が自分で依頼を立てられると、
          引き渡しの記録が「誰が渡したか」を表さなくなる */}
      {isSales && (
        <section className="space-y-2 rounded border border-gray-200 bg-gray-50 p-4">
          <h2 className="text-lg font-semibold text-gray-900">経路設計への引き渡し</h2>
          {booking.routingStatus === 'NOT_ROUTED' && (
            <>
              <p className="text-sm text-gray-700">
                内容を確かめてから引き渡してください。引き渡すと、経路設計者の一覧に表示されます。
              </p>
              <button
                type="button"
                onClick={() => request.mutate()}
                disabled={request.isPending}
                className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
              >
                経路設計を依頼する
              </button>
            </>
          )}
          {/* 差し戻された予約を営業が返せないと、荷主と話がついても予約が止まったままになる
              （ADR-020 決定 7 の裏側） */}
          {booking.routingStatus === 'CONSULTATION_REQUESTED' && (
            <>
              <p className="text-sm text-gray-700">
                経路設計者から条件の協議を求められています。荷主と条件が決まったら、
                もう一度引き渡してください。
              </p>
              <button
                type="button"
                onClick={() => request.mutate()}
                disabled={request.isPending}
                className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
              >
                経路設計に再依頼する
              </button>
            </>
          )}
          {booking.routingStatus !== 'NOT_ROUTED'
            && booking.routingStatus !== 'CONSULTATION_REQUESTED' && (
            <p className="text-sm text-gray-700">
              この予約はすでに引き渡し済みです（
              {ROUTING_STATUS_LABELS[booking.routingStatus] ?? booking.routingStatus}）。
            </p>
          )}
        </section>
      )}

      {/* 割り当てられた旅程（US09）。**経路が決まっていない予約では枠ごと出さない**。
          空の表を出すと「区間が 0 件の旅程がある」ように見える */}
      {/* null も未設定も「旅程が無い」。項目ごと省く応答もありうる */}
      {(booking.itinerary?.length ?? 0) > 0 && (
        <section className="space-y-2 rounded border border-gray-200 p-4">
          <h2 className="text-lg font-semibold text-gray-900">
            割り当て経路（旅程・{booking.itinerary?.length} 区間）
          </h2>
          <div className="overflow-x-auto">
            <table className="min-w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-gray-300 text-left">
                  <th className="py-2">順</th>
                  <th>航海</th>
                  <th>積込</th>
                  <th>荷降し</th>
                  <th>積込日時</th>
                  <th>荷降し日時</th>
                </tr>
              </thead>
              <tbody>
                {(booking.itinerary ?? []).map((leg, index) => (
                  <tr key={`${leg.voyageNumber}-${leg.loadUnLocode}`} className="border-b">
                    <td className="py-2">{index + 1}</td>
                    <td>{leg.voyageNumber}</td>
                    {/* 港は名前で、コードは併記にとどめる（表示規約） */}
                    <td>
                      {leg.loadName}
                      <span className="ml-1 text-gray-500">({leg.loadUnLocode})</span>
                    </td>
                    <td>
                      {leg.unloadName}
                      <span className="ml-1 text-gray-500">({leg.unloadUnLocode})</span>
                    </td>
                    {/* 日時は業務タイムゾーン（表示規約） */}
                    <td>{formatBusinessDateTime(leg.loadTime)}</td>
                    <td>{formatBusinessDateTime(leg.unloadTime)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* 経路設計の入口。**状態で出し分ける**（ADR-015・ADR-020）。
          引き渡されていない予約に経路を組むのは、営業がまだ作業中のものに手を出すことになる。
          サーバは引き渡し済み・確定済み・差し戻し済みを開き、それ以外は存在しない予約と
          同じ 404 を返す（RoutingStatus#visibleToRoutingPlanner）。出し分けはそれに合わせる */}
      {isRoutingPlanner && (
        <section className="space-y-2 rounded border border-gray-200 bg-gray-50 p-4">
          <h2 className="text-lg font-semibold text-gray-900">経路設計</h2>
          {booking.routingStatus === 'ROUTING_REQUESTED' && (
            <>
              <p className="text-sm text-gray-700">
                期限内に着く経路の候補を算出します。条件はこの予約から引き継ぎます。
              </p>
              <Link
                to={`/routing/design/${booking.bookingId}`}
                className="inline-block rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
              >
                経路を割り当て
              </Link>
            </>
          )}
          {/* 航海の遅延・欠航で差し替えることがある（ADR-020 決定 4）。
              決まったら終わりにすると、差し替えの入口がどこにも無くなる */}
          {booking.routingStatus === 'ROUTED' && (
            <>
              <p className="text-sm text-gray-700">
                この予約には経路が決まっています。航海の変更があれば見直せます。
              </p>
              <Link
                to={`/routing/design/${booking.bookingId}`}
                className="inline-block rounded border border-gray-400 px-4 py-2 text-sm text-gray-700"
              >
                経路を見直す
              </Link>
            </>
          )}
          {/* 差し戻し中も経路設計へ戻れる。営業と話がついたあとに続きができないと、
              差し戻した本人が自分の仕事に戻れない（ADR-020 決定 7） */}
          {booking.routingStatus === 'CONSULTATION_REQUESTED' && (
            <>
              <p className="text-sm text-gray-700">
                この予約は営業へ戻しています。条件が決まったら、もう一度経路を探せます。
              </p>
              <Link
                to={`/routing/design/${booking.bookingId}`}
                className="inline-block rounded border border-gray-400 px-4 py-2 text-sm text-gray-700"
              >
                経路設計を開く
              </Link>
            </>
          )}
          {booking.routingStatus === 'NOT_ROUTED' && (
            <p className="text-sm text-gray-700">
              この予約はまだ経路設計に引き渡されていません。
            </p>
          )}
        </section>
      )}

      <p className="text-sm text-gray-600">
        内容に不備があるときは、いまのところ予約を作り直してください。予約の訂正は次のリリースで対応します。
      </p>
    </div>
  )
}
