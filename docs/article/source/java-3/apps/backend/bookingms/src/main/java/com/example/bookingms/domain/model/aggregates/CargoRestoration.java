package com.example.bookingms.domain.model.aggregates;

import java.time.Instant;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.bookingms.domain.model.valueobjects.CargoStatus;
import com.example.bookingms.domain.model.valueobjects.Misroute;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.TrackingNumber;

/**
 * 永続化された行から {@link Cargo} を組み立てる。
 *
 * <p><strong>集約から分けたのは、責務が違うからである。</strong>集約が持つのは業務の
 * 振る舞い（何ができて、何を断るか）であり、復元は<strong>永続化の都合</strong>である。
 * 実際、復元の入口には業務の検査が 1 つも無い——列が無かったころの行や、規則が変わる前に
 * 入った行が読めなくなるためである（不変条件の追加は既存行を壊す）。
 *
 * <p>行数の上限に当たったから割ったのではない。<strong>上限は合図であって、割り方の
 * 基準ではない。</strong>基準は責務である。
 */
public final class CargoRestoration {

    private CargoRestoration() {
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification) {
        return restore(id, bookingId, shipperId, status, specification, routeSpecification, null);
    }

    /** 旅程を伴って復元する。ここでは検査しない。 */
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary) {
        return restore(id, bookingId, shipperId, status, specification, routeSpecification,
                itinerary, null, null);
    }

    /**
     * 通知の記録と追跡番号まで伴って復元する。ここでは検査しない（[ADR-012]）。
     *
     * <p><strong>不変条件（`ROUTE_NOTIFIED` 以降なら通知の記録がある）をここで検査しない。</strong>
     * 列が無かったころの行が読めなくなる。守るのは新しく受け入れるときだけでよい。
     */
    @SuppressWarnings("java:S107")
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary, RouteNotification notification,
            TrackingNumber trackingNumber) {
        return restore(id, bookingId, shipperId, status, specification, routeSpecification,
                itinerary, notification, trackingNumber, null, null);
    }

    /** 最後の荷役まで伴って復元する。ここでは検査しない（[ADR-025] 決定 4）。 */
    @SuppressWarnings("java:S107")
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary, RouteNotification notification,
            TrackingNumber trackingNumber, String lastHandlingLocationUnLocode,
            Instant lastHandlingAt) {
        return restore(id, bookingId, shipperId, status, specification, routeSpecification,
                itinerary, notification, trackingNumber, lastHandlingLocationUnLocode,
                lastHandlingAt, null);
    }

    /**
     * 誤配の記録まで伴って復元する（[ADR-026] 決定 3）。ここでは検査しない。
     *
     * <p><strong>書き忘れると、その項目だけが読み戻しで消える。</strong>誤配の事実は
     * 料金調整の根拠であり、消えたことは請求の段まで気づかれない。
     */
    @SuppressWarnings("java:S107")
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary, RouteNotification notification,
            TrackingNumber trackingNumber, String lastHandlingLocationUnLocode,
            Instant lastHandlingAt, Misroute misroute) {
        return new Cargo(id, bookingId, shipperId, status, specification, routeSpecification,
                itinerary, notification, trackingNumber, lastHandlingLocationUnLocode,
                lastHandlingAt, misroute);
    }

}

