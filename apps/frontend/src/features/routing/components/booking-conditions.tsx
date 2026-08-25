import type { Booking } from "../../booking/types";
import { ROUTING_CARGO_TYPE_LABELS, type RoutingCargoType } from "../types";

/**
 * 経路設計の対象になっている予約の条件。
 *
 * <p><strong>誤配のあとは出発地が現在地になる</strong>（US28-4・[ADR-026] 決定 4）。
 * ここが元の港のままだと、経路設計者は候補の出発地が違うことに気づけない。当初の港も
 * 併記する——どこから外れたかは、組み直しの判断材料になる。
 */
export function BookingConditions({
  booking,
  cargoType,
  misrouted,
  searchOrigin,
}: {
  booking: Booking;
  cargoType: RoutingCargoType;
  /** いま経路から外れているか。**記録の有無ではなく状態で決める** */
  misrouted: boolean;
  /** 候補を探す起点。誤配なら貨物の現在地 */
  searchOrigin: string;
}) {
  return (
    <dl className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4">
      <div>
        <dt className="text-sm text-gray-600">予約番号</dt>
        <dd>{booking.bookingId}</dd>
      </div>
      <div>
        <dt className="text-sm text-gray-600">荷主</dt>
        <dd>{booking.shipperName ?? "―"}</dd>
      </div>
      <div>
        <dt className="text-sm text-gray-600">出発地</dt>
        <dd>
          {misrouted ? (
            <>
              {searchOrigin}
              <span className="ml-2 text-sm text-gray-600">
                現在地（当初は {booking.originName}）
              </span>
            </>
          ) : (
            <>
              {booking.originName}（{booking.originUnLocode}）
            </>
          )}
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
        <dd>{booking.weightKg.toLocaleString("ja-JP")} kg</dd>
      </div>
    </dl>
  );
}
