import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../lib/api-client'
import { useAuthStore } from '../stores/auth-store'
import {
  useBooking,
  useConfirmBooking,
  useIssueTrackingNumber,
  useNotifyShipper,
  useRequestRouting,
  useReturnToRouting,
} from '../features/booking/queries'
import {
  BOOKING_STATUS_LABELS,
  CARGO_TYPE_LABELS,
  ROUTING_STATUS_LABELS,
} from '../features/booking/types'
import { formatBusinessDateTime } from '../lib/business-time'

/**
 * 状態ごとの手番（[ADR-021] 決定 6）。
 *
 * 出さないと、一覧に並んだ予約のどれが自分の仕事か分からず、状態を足した意味が無くなる。
 * **判定を画面に散らかさない**——ここ 1 か所に置く。
 */
const TURN_LABELS: Record<string, string> = {
  PRELIMINARY: '営業担当者の手番です。内容を確かめて経路設計を依頼してください。',
  ROUTE_PROPOSED: '営業担当者の手番です。経路が決まりました。荷主へ通知してください。',
  ROUTE_NOTIFIED: '荷主の手番です。返事を待っています。',
  CONFIRMED: '経路設計者の手番です。追跡番号の発行を待っています。',
  TRACKING_ISSUED: '荷役の手番です。貨物の受け取りを待っています。',
}

/** 旅程から所要日数を出す。通知内容の確認（US12-2）に使う。 */
function transitDaysOf(loadTime: string, unloadTime: string): number {
  const millis = new Date(unloadTime).getTime() - new Date(loadTime).getTime()
  return Math.max(1, Math.round(millis / (24 * 60 * 60 * 1000)))
}

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
  const notify = useNotifyShipper(bookingId)
  const confirm = useConfirmBooking(bookingId)
  const returnToRouting = useReturnToRouting(bookingId)
  const issueTracking = useIssueTrackingNumber(bookingId)
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

      {/* 手番。いまの状態で誰が動くかを 1 行で出す（ADR-021 決定 6） */}
      <p className="rounded border border-gray-200 bg-blue-50 p-3 text-sm text-gray-800">
        {TURN_LABELS[booking.bookingStatus] ?? ''}
      </p>

      {/* 通知の記録（US12-4）。メールは送っていないため、これが唯一の証跡である。
          null も未設定も「記録が無い」。項目ごと省く応答もありうる（旅程と同じ扱い） */}
      {(booking.routeNotifiedAt ?? null) !== null && (
        <p className="text-sm text-gray-700">
          荷主へ通知しました（{formatBusinessDateTime(booking.routeNotifiedAt ?? '')}・
          {booking.routeNotifiedBy}）。
        </p>
      )}

      {/* 発行済みの追跡番号（US14-4 の代替）。荷主には届いていないため、営業が伝える */}
      {(booking.trackingNumber ?? null) !== null && (
        <section className="space-y-1 rounded border border-cyan-200 bg-cyan-50 p-4">
          <h2 className="text-lg font-semibold text-gray-900">追跡番号</h2>
          <p className="font-mono text-lg text-gray-900">{booking.trackingNumber}</p>
          <p className="text-sm text-gray-700">
            <strong>荷主には自動で送られていません。</strong>
            この番号を電話・メールで伝えてください。荷主が自分で照会する画面は次のリリースで
            使えるようになります。
          </p>
        </section>
      )}

      {/* 荷主への通知・確定・経路設計へ戻す（US12・US13）。営業担当者の操作である。
          荷主とのやりとりを持っているのは営業であり、経路設計者が直接連絡すると、
          営業が把握していない約束ができる。
          **状態で出し分ける**——すべての操作を常に出して押したときに断ると、
          利用者は「押せるのにできない」を毎回学び直すことになる */}
      {isSales && (booking.bookingStatus === 'ROUTE_PROPOSED'
        || booking.bookingStatus === 'ROUTE_NOTIFIED') && (
        <section className="space-y-3 rounded border border-gray-200 bg-gray-50 p-4">
          <h2 className="text-lg font-semibold text-gray-900">荷主とのやりとり</h2>

          {/* 送る前に、何を伝えることになるかを同じ画面で確認できるようにする（US12-2）。
              確認せずに送れる形にすると、営業は送ってから旅程を見ることになる */}
          {(booking.itinerary?.length ?? 0) > 0 && (
            <dl className="grid grid-cols-[10rem_1fr] gap-y-1 text-sm text-gray-800">
              <dt className="font-medium">経由港</dt>
              <dd>
                {(booking.itinerary ?? []).length === 1
                  ? '直行（積み替えなし）'
                  : (booking.itinerary ?? [])
                      .slice(0, -1)
                      .map((leg) => leg.unloadName)
                      .join(' → ')}
              </dd>
              <dt className="font-medium">所要日数</dt>
              <dd>
                約{' '}
                {transitDaysOf(
                  (booking.itinerary ?? [])[0].loadTime,
                  (booking.itinerary ?? [])[(booking.itinerary ?? []).length - 1].unloadTime,
                )}{' '}
                日
              </dd>
              <dt className="font-medium">到着予定</dt>
              <dd>
                {formatBusinessDateTime(
                  (booking.itinerary ?? [])[(booking.itinerary ?? []).length - 1].unloadTime,
                )}
              </dd>
              <dt className="font-medium">費用の概算</dt>
              <dd>
                経路設計の画面で確認してください（<strong>概算</strong>です。正式な料金は
                精算時に確定します）
              </dd>
            </dl>
          )}

          <p className="rounded border border-amber-200 bg-amber-50 p-2 text-sm text-amber-900">
            <strong>この操作ではメールは送られません。</strong>
            荷主へは電話・メールで連絡してください。ここに残るのは「通知した」という記録です。
          </p>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => notify.mutate()}
              disabled={notify.isPending}
              className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {booking.bookingStatus === 'ROUTE_NOTIFIED'
                ? 'もう一度通知する'
                : '荷主へ通知する'}
            </button>
            {/* 通知していない予約は確定できない（ADR-021 決定 1）。
                確定は「荷主の合意を得た」という業務上の事実である */}
            {booking.bookingStatus === 'ROUTE_NOTIFIED' && (
              <>
                <button
                  type="button"
                  onClick={() => confirm.mutate()}
                  disabled={confirm.isPending}
                  className="rounded bg-green-700 px-4 py-2 text-white hover:bg-green-800 disabled:opacity-50"
                >
                  予約を確定する
                </button>
                {/* 戻すと経路の状態も作業待ちに戻り、経路設計者の一覧に現れる
                    （ADR-021 決定 4）。BookingStatus だけ戻しても伝わらない */}
                <button
                  type="button"
                  onClick={() => returnToRouting.mutate()}
                  disabled={returnToRouting.isPending}
                  className="rounded border border-gray-400 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 disabled:opacity-50"
                >
                  経路設計へ戻す
                </button>
              </>
            )}
          </div>
          <p className="text-sm text-gray-600">
            荷主が経路の変更を希望したら「経路設計へ戻す」を押してください。経路設計者の
            「経路設計を待っている予約」に表示されます。
          </p>
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

          {/* 追跡番号の発行（US14）。確定した予約にだけ出す。
              二重に発行すると、荷主に伝えた番号で追えなくなる */}
          {booking.bookingStatus === 'CONFIRMED' && (
            <div className="space-y-2 border-t border-gray-300 pt-3">
              <p className="text-sm text-gray-700">
                この予約は確定しています。追跡番号を発行すると、貨物の追跡が始まります。
              </p>
              <button
                type="button"
                onClick={() => issueTracking.mutate()}
                disabled={issueTracking.isPending}
                className="rounded bg-cyan-700 px-4 py-2 text-white hover:bg-cyan-800 disabled:opacity-50"
              >
                追跡番号を発行する
              </button>
            </div>
          )}
        </section>
      )}

      <p className="text-sm text-gray-600">
        内容に不備があるときは、いまのところ予約を作り直してください。予約の訂正は次のリリースで対応します。
      </p>
    </div>
  )
}
