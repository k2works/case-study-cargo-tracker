import type { UseMutationResult } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import type { Booking } from "../types";
import { can } from "../types";

/**
 * 経路設計の入口（US09・[ADR-020]）。経路設計者の手番。
 *
 * <p>荷主とのやりとり（{@link ShipperDialogueSection}）と分けたのは、手番が違うからである。
 *
 * <p>出し分けは<strong>サーバが返す「行える操作」</strong>に従う。引き渡されていない予約に
 * 経路を組むのは、営業がまだ作業中のものに手を出すことになる。
 */
export function RouteDesignSection({
  booking,
  isRoutingPlanner,
  issueTracking,
}: Readonly<{
  booking: Booking;
  isRoutingPlanner: boolean;
  issueTracking: UseMutationResult<unknown, unknown, void, unknown>;
}>) {
  return (
    <>
    {/* 経路設計の入口。**状態で出し分ける**（ADR-015・ADR-020）。
        引き渡されていない予約に経路を組むのは、営業がまだ作業中のものに手を出すことになる。
        サーバは引き渡し済み・確定済み・差し戻し済みを開き、それ以外は存在しない予約と
        同じ 404 を返す（RoutingStatus#visibleToRoutingPlanner）。出し分けはそれに合わせる */}
    {isRoutingPlanner && (
      <section className="space-y-2 rounded border border-gray-200 bg-gray-50 p-4">
        <h2 className="text-lg font-semibold text-gray-900">経路設計</h2>
        {booking.routingStatus === "ROUTING_REQUESTED" && (
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
            決まったら終わりにすると、差し替えの入口がどこにも無くなる。
            **確定したあとは差し替えられない**（ADR-021 決定 3）。入口を出すと、
            候補を出し、選び、確認まで進んでから断られることになる */}
        {booking.routingStatus === "ROUTED" && can(booking, "ASSIGN_ROUTE") && (
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
        {booking.routingStatus === "ROUTED" && !can(booking, "ASSIGN_ROUTE") && (
          <p className="text-sm text-gray-700">
            この予約は確定しています。
            {/* 改行を空白と読ませない（日本語は語間を空けない） */}
            <strong>経路は差し替えられません。</strong>
            {""}
            航海の変更で経路を変える必要があるときは、運用のルールに従って社内で調整して
            ください（システムでの変更は次のリリース以降です）。
          </p>
        )}
        {/* 差し戻し中も経路設計へ戻れる。営業と話がついたあとに続きができないと、
            差し戻した本人が自分の仕事に戻れない（ADR-020 決定 7） */}
        {booking.routingStatus === "CONSULTATION_REQUESTED" && (
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
        {booking.routingStatus === "NOT_ROUTED" && (
          <p className="text-sm text-gray-700">
            この予約はまだ経路設計に引き渡されていません。
          </p>
        )}

        {/* 追跡番号の発行（US14）。確定した予約にだけ出す。
            二重に発行すると、荷主に伝えた番号で追えなくなる */}
        {can(booking, "ISSUE_TRACKING_NUMBER") && (
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
    </>
  );
}
