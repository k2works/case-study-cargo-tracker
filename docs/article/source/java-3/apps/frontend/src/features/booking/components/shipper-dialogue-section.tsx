import type { UseMutationResult } from "@tanstack/react-query";
import type { Booking } from "../types";
import { can } from "../types";
import { formatBusinessDateTime } from "../../../lib/business-time";
import { transitDaysBetween } from "../../routing/transit-days";

type Mutation = UseMutationResult<unknown, unknown, void, unknown>;

/**
 * 荷主とのやりとり（US12・US13）。営業担当者の手番。
 *
 * <p>経路設計の入口（{@link RouteDesignSection}）と分けたのは、<strong>手番が違う</strong>
 * からである。営業と経路設計者では、見るものも押せるものも変わる理由も違う。
 *
 * <p>出し分けは<strong>サーバが返す「行える操作」</strong>に従う（[ADR-021]）。
 * ここで状態名を見比べると、遷移の規則が画面にも住み着く。
 */
export function ShipperDialogueSection({
  booking,
  isSales,
  notify,
  confirm,
  returnToRouting,
}: Readonly<{
  booking: Booking;
  isSales: boolean;
  notify: Mutation;
  confirm: Mutation;
  returnToRouting: Mutation;
}>) {
  return (
    <>
    {/* 荷主への通知・確定・経路設計へ戻す（US12・US13）。営業担当者の操作である。
        荷主とのやりとりを持っているのは営業であり、経路設計者が直接連絡すると、
        営業が把握していない約束ができる。
        **状態で出し分ける**——すべての操作を常に出して押したときに断ると、
        利用者は「押せるのにできない」を毎回学び直すことになる */}
    {isSales && can(booking, "NOTIFY_SHIPPER") && (
        <section className="space-y-3 rounded border border-gray-200 bg-gray-50 p-4">
          <h2 className="text-lg font-semibold text-gray-900">
            荷主とのやりとり
          </h2>

          {/* 送る前に、何を伝えることになるかを同じ画面で確認できるようにする（US12-2）。
            確認せずに送れる形にすると、営業は送ってから旅程を見ることになる */}
          {(booking.itinerary?.length ?? 0) > 0 && (
            <dl className="grid grid-cols-[10rem_1fr] gap-y-1 text-sm text-gray-800">
              <dt className="font-medium">経由港</dt>
              <dd>
                {(booking.itinerary ?? []).length === 1
                  ? "直行（積み替えなし）"
                  : (booking.itinerary ?? [])
                      .slice(0, -1)
                      .map((leg) => leg.unloadName)
                      .join(" → ")}
              </dd>
              <dt className="font-medium">所要日数</dt>
              <dd>
                約{" "}
                {transitDaysBetween(
                  (booking.itinerary ?? [])[0].loadTime,
                  (booking.itinerary ?? [])[
                    (booking.itinerary ?? []).length - 1
                  ].unloadTime,
                )}{" "}
                日
              </dd>
              <dt className="font-medium">到着予定</dt>
              <dd>
                {formatBusinessDateTime(
                  (booking.itinerary ?? [])[
                    (booking.itinerary ?? []).length - 1
                  ].unloadTime,
                )}
              </dd>
              <dt className="font-medium">費用の概算</dt>
              <dd>
                経路設計の画面で確認してください（<strong>概算</strong>
                {/* 改行を空白と読ませない（日本語は語間を空けない） */}
                です。正式な料金は 精算時に確定します）
              </dd>
            </dl>
          )}

          <p className="rounded border border-amber-200 bg-amber-50 p-2 text-sm text-amber-900">
            <strong>この操作ではメールは送られません。</strong>
            {""}
            荷主へは電話・メールで連絡してください。ここに残るのは「通知した」という記録です。
          </p>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => notify.mutate()}
              disabled={notify.isPending}
              className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {/* 「もう一度」かどうかは遷移の可否ではなく、通知の記録があるかである */}
              {booking.routeNotifiedAt ? "もう一度通知する" : "荷主へ通知する"}
            </button>
            {/* 通知していない予約は確定できない（ADR-021 決定 1）。
              確定は「荷主の合意を得た」という業務上の事実である */}
            {can(booking, "CONFIRM") && (
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
    </>
  );
}
