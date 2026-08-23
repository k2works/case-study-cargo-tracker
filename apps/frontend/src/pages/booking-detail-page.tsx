import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../lib/api-client";
import { useAuthStore } from "../stores/auth-store";
import {
  useBooking,
  useConfirmBooking,
  useIssueTrackingNumber,
  useNotifyShipper,
  useRequestRouting,
  useReturnToRouting,
  useReviseSchedule,
} from "../features/booking/queries";
import {
  BOOKING_STATUS_LABELS,
  CARGO_TYPE_LABELS,
  ROUTING_STATUS_LABELS,
  can,
} from "../features/booking/types";
import { formatBusinessDateTime } from "../lib/business-time";
import { ItineraryTable } from "../features/booking/components/itinerary-table";
import { RouteDesignSection } from "../features/booking/components/route-design-section";
import { ScheduleRevisionSection } from "../features/booking/components/schedule-revision-section";
import { ShipperDialogueSection } from "../features/booking/components/shipper-dialogue-section";


/**
 * 状態ごとの手番（[ADR-021] 決定 6）。
 *
 * 出さないと、一覧に並んだ予約のどれが自分の仕事か分からず、状態を足した意味が無くなる。
 * **判定を画面に散らかさない**——ここ 1 か所に置く。
 */
const TURN_LABELS: Record<string, string> = {
  PRELIMINARY:
    "営業担当者の手番です。内容を確かめて経路設計を依頼してください。",
  ROUTE_PROPOSED:
    "営業担当者の手番です。経路が決まりました。荷主へ通知してください。",
  ROUTE_NOTIFIED: "荷主の手番です。返事を待っています。",
  CONFIRMED: "経路設計者の手番です。追跡番号の発行を待っています。",
  TRACKING_ISSUED: "荷役の手番です。貨物の受け取りを待っています。",
};

/**
 * 予約の詳細（US06）。
 *
 * 営業担当者が引き渡す前に内容を確かめ、経路設計者が受け取った予約の中身を見る画面。
 * 中身が見えないまま引き渡すと、経路設計者は不備に気づけないまま経路を組むことになる。
 */
export function BookingDetailPage() {
  const { bookingId = "" } = useParams();
  const { data: booking, isLoading, isError } = useBooking(bookingId);
  const request = useRequestRouting(bookingId);
  const notify = useNotifyShipper(bookingId);
  const confirm = useConfirmBooking(bookingId);
  const returnToRouting = useReturnToRouting(bookingId);
  const issueTracking = useIssueTrackingNumber(bookingId);
  const revise = useReviseSchedule(bookingId);
  const [revising, setRevising] = useState(false);
  // 本番と同じ判定を使う。ここで独自に書くと、検査だけが正しく本番の誤りを素通りさせる
  const isSales = useAuthStore((state) => state.hasAnyRole(["ROLE_SALES"]));
  const isRoutingPlanner = useAuthStore((state) =>
    state.hasAnyRole(["ROLE_ROUTING"]),
  );

  function requestFailureMessage(): string | null {
    if (request.error === null || request.error === undefined) {
      return null;
    }
    // 409 は入力の誤りではない。予約の状態がその操作を許さないという返事である
    if (request.error instanceof ApiError && request.error.status === 409) {
      const body = request.error.body as { message?: string } | undefined;
      return body?.message ?? "この予約は経路設計を依頼できません。";
    }
    return "経路設計を依頼できませんでした。時間をおいて再度お試しください。";
  }

  if (isLoading) {
    return <p className="text-gray-600">読み込んでいます…</p>;
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
    );
  }

  const failure = requestFailureMessage();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">
          予約 {booking.bookingId}
        </h1>
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
        <p
          role="alert"
          className="rounded border border-red-200 bg-red-50 p-3 text-red-700"
        >
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
                {BOOKING_STATUS_LABELS[booking.bookingStatus] ??
                  booking.bookingStatus}
              </td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">経路</th>
              <td className="px-3 py-2">
                {ROUTING_STATUS_LABELS[booking.routingStatus] ??
                  booking.routingStatus}
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
              <td className="px-3 py-2">{booking.shipperName ?? "（不明）"}</td>
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
              <td className="px-3 py-2">
                {booking.departureDate ?? "（指定なし）"}
              </td>
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
              <td className="px-3 py-2">
                {booking.quantity ?? "（指定なし）"}
              </td>
            </tr>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">品名</th>
              <td className="px-3 py-2">
                {booking.description ?? "（指定なし）"}
              </td>
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
          <h2 className="text-lg font-semibold text-gray-900">
            経路設計への引き渡し
          </h2>
          {can(booking, "REQUEST_ROUTING") && booking.routingStatus === "NOT_ROUTED" && (
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
          {can(booking, "REQUEST_ROUTING") &&
            booking.routingStatus === "CONSULTATION_REQUESTED" && (
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
          {booking.routingStatus !== "NOT_ROUTED" &&
            booking.routingStatus !== "CONSULTATION_REQUESTED" && (
              <p className="text-sm text-gray-700">
                この予約はすでに引き渡し済みです（
                {ROUTING_STATUS_LABELS[booking.routingStatus] ??
                  booking.routingStatus}
                ）。
              </p>
            )}
        </section>
      )}

      <ItineraryTable booking={booking} />

      <ScheduleRevisionSection
        booking={booking}
        isSales={isSales}
        revise={revise}
        revising={revising}
        setRevising={setRevising}
      />

      {/* 手番。いまの状態で誰が動くかを 1 行で出す（ADR-021 決定 6） */}
      <p className="rounded border border-gray-200 bg-blue-50 p-3 text-sm text-gray-800">
        {TURN_LABELS[booking.bookingStatus] ?? ""}
      </p>

      {/* 通知の記録（US12-4）。メールは送っていないため、これが唯一の証跡である。
          null も未設定も「記録が無い」。項目ごと省く応答もありうる（旅程と同じ扱い） */}
      {(booking.routeNotifiedAt ?? null) !== null && (
        <p className="text-sm text-gray-700">
          荷主へ通知しました（
          {formatBusinessDateTime(booking.routeNotifiedAt ?? "")}・
          {booking.routeNotifiedBy}）。
        </p>
      )}

      {/* 発行済みの追跡番号（US14-4 の代替）。荷主には届いていないため、営業が伝える */}
      {(booking.trackingNumber ?? null) !== null && (
        <section className="space-y-1 rounded border border-cyan-200 bg-cyan-50 p-4">
          <h2 className="text-lg font-semibold text-gray-900">追跡番号</h2>
          <p className="font-mono text-lg text-gray-900">
            {booking.trackingNumber}
          </p>
          <p className="text-sm text-gray-700">
            <strong>荷主には自動で送られていません。</strong>
            {""}
            この番号を電話・メールで伝えてください。荷主が自分で照会する画面は次のリリースで
            使えるようになります。
          </p>
        </section>
      )}

      <ShipperDialogueSection
        booking={booking}
        isSales={isSales}
        notify={notify}
        confirm={confirm}
        returnToRouting={returnToRouting}
      />

      <RouteDesignSection
        booking={booking}
        isRoutingPlanner={isRoutingPlanner}
        issueTracking={issueTracking}
      />

      <p className="text-sm text-gray-600">
        出発地・目的地・貨物の内容に不備があるときは、予約を作り直してください。
        日程（到着期限・出発希望日）は、経路設計に引き渡す前と、営業へ戻された予約なら直せます。
      </p>
    </div>
  );
}
