package com.example.cargotracker.booking.infrastructure.query;

import com.example.cargotracker.booking.infrastructure.persistence.CargoLegMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoNotificationMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoRevisionMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AffectedBookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AffectedBookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ConditionReviewListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ConditionReviewView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindConditionReviewsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingNotificationsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRouteConditionQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.NotificationListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.NotificationView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.RouteConditionView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingConfirmationListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingTrackingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingTrackingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindAwaitingTrackingNumberQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingConfirmationView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindAwaitingConfirmationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountAwaitingNotificationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountBookingsByStatusQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsByVoyageQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingItineraryQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingRevisionsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ItineraryLegView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ItineraryView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.RevisionListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.RevisionView;
import java.util.List;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 予約の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class BookingQueryHandler {

    private final CargoSummaryMapper cargos;
    private final CargoRevisionMapper revisions;
    private final CargoLegMapper legs;
    private final CargoNotificationMapper notifications;

    public BookingQueryHandler(CargoSummaryMapper cargos, CargoRevisionMapper revisions,
            CargoLegMapper legs, CargoNotificationMapper notifications) {
        this.cargos = cargos;
        this.revisions = revisions;
        this.legs = legs;
        this.notifications = notifications;
    }

    /** 確定した旅程（US09）。まだ決まっていなければ空。 */
    @QueryHandler
    public ItineraryView handle(FindBookingItineraryQuery query) {
        return new ItineraryView(legs.findByBooking(query.bookingId()).stream()
                .map(row -> new ItineraryLegView(row.legSeq(), row.voyageNumber(),
                        row.loadUnlocode(), row.unloadUnlocode(), row.loadAt(), row.unloadAt()))
                .toList());
    }

    /**
     * その航海で経路を組んだ予約（S34 / US24）。組んでいなければ空。
     *
     * <p>止めても予約側の旅程は自動では戻らない。<b>止める前に</b>誰を巻き込むかを
     * 読めるようにする（IT5 引き継ぎ 2）。</p>
     */
    @QueryHandler
    public AffectedBookingListView handle(FindBookingsByVoyageQuery query) {
        return new AffectedBookingListView(
                legs.findBookingsByVoyage(query.voyageNumber()).stream()
                        .map(row -> new AffectedBookingView(row.bookingId(), row.bookingNumber(),
                                row.bookingStatus(), row.routingStatus()))
                        .toList());
    }

    /** 見直しを頼まれている予約（S02 / 営業。US10 §4）。古い依頼から順に返す。 */
    @QueryHandler
    public ConditionReviewListView handle(FindConditionReviewsQuery query) {
        return new ConditionReviewListView(cargos.findConditionReviews(query.limit()).stream()
                .map(row -> new ConditionReviewView(row.bookingId(), row.bookingNumber(),
                        row.reason(), row.requestedAt()))
                .toList());
    }

    /**
     * 調整された探索条件（US10）。調整していなければ空。
     *
     * <p>予約そのものが無いときも空を返す。{@code null} を返すと、呼ぶ側が
     * 「予約が無い」と「条件が無い」を分けて扱うことになるが、候補算出は先に
     * 予約の有無を見ている。</p>
     */
    @QueryHandler
    public RouteConditionView handle(FindRouteConditionQuery query) {
        CargoSummaryMapper.RouteConditionRow row = cargos.findRouteCondition(query.bookingId());
        if (row == null || row.excludeUnlocodes() == null && row.departFromUnlocode() == null) {
            return new RouteConditionView(List.of(), null);
        }
        return new RouteConditionView(parsePorts(row.excludeUnlocodes()),
                row.departFromUnlocode());
    }

    /** 荷主へ通知していない経路確定済みの予約の件数（S02 / 営業。US12）。 */
    @QueryHandler
    public Integer handle(CountAwaitingNotificationQuery query) {
        return cargos.countAwaitingNotification();
    }

    /** 確定を待っている予約（S02 / 営業。US13 §受入基準 3）。 */
    @QueryHandler
    public AwaitingConfirmationListView handle(FindAwaitingConfirmationQuery query) {
        return new AwaitingConfirmationListView(
                cargos.findAwaitingConfirmation(Math.clamp(query.limit(), 1, 200)).stream()
                        .map(row -> new AwaitingConfirmationView(
                                row.bookingId(), row.bookingNumber(), row.notifiedAt()))
                        .toList());
    }

    /** 追跡番号の発行を待っている予約（S02 / 経路設計者。US13 §受入基準 3）。 */
    @QueryHandler
    public AwaitingTrackingListView handle(FindAwaitingTrackingNumberQuery query) {
        return new AwaitingTrackingListView(
                cargos.findAwaitingTrackingNumber(Math.clamp(query.limit(), 1, 200)).stream()
                        .map(row -> new AwaitingTrackingView(
                                row.bookingId(), row.bookingNumber(), row.confirmedAt()))
                        .toList());
    }

    /** 通知履歴（US12 §受入基準 4）。一度も通知していなければ空。 */
    @QueryHandler
    public NotificationListView handle(FindBookingNotificationsQuery query) {
        return new NotificationListView(
                notifications.findByBooking(query.bookingId()).stream()
                        .map(row -> new NotificationView(row.notifiedAt(), row.recipientEmail(),
                                row.summary(), row.notifiedBy()))
                        .toList());
    }

    /** 修正履歴（US32 §受入基準 4）。一度も直していなければ空。 */
    @QueryHandler
    public RevisionListView handle(FindBookingRevisionsQuery query) {
        return new RevisionListView(revisions.findByBooking(query.bookingId()).stream()
                .map(row -> new RevisionView(row.updatedAt(), row.updatedBy(),
                        row.fieldLabel(), row.beforeValue(), row.afterValue()))
                .toList());
    }

    @QueryHandler
    public BookingView handle(FindBookingQuery query) {
        CargoSummaryMapper.CargoSummaryRow row = cargos.findById(query.bookingId());
        return row == null ? null : toView(row);
    }

    @QueryHandler
    public BookingListView handle(FindBookingsQuery query) {
        int size = Math.clamp(query.size(), 1, 200);
        int offset = Math.max(query.page(), 0) * size;
        return new BookingListView(
                cargos.findAll(query.includeFinished(), size, offset).stream()
                        .map(BookingQueryHandler::toView).toList(),
                cargos.countAll(query.includeFinished()));
    }

    @QueryHandler
    public BookingListView handle(FindRoutingWorklistQuery query) {
        int size = Math.clamp(query.size(), 1, 200);
        int offset = Math.max(query.page(), 0) * size;
        return new BookingListView(
                cargos.findRoutingWorklist(query.includeRouted(), size, offset).stream()
                        .map(BookingQueryHandler::toView).toList(),
                cargos.countRoutingWorklist(query.includeRouted()));
    }

    @QueryHandler
    public Integer handle(CountBookingsByStatusQuery query) {
        return cargos.countByStatus(query.bookingStatus());
    }

    private static BookingView toView(CargoSummaryMapper.CargoSummaryRow row) {
        return new BookingView(
                row.bookingId(), row.bookingNumber(), row.shipperId(), row.shipperName(),
                row.originUnlocode(), row.destinationUnlocode(), row.arrivalDeadline(),
                row.cargoType(), row.weightKg(), row.lengthCm(), row.widthCm(), row.heightCm(),
                row.quantity(), row.productName(), row.hazardImoClass(), row.hazardUnNumber(),
                row.temperatureMinC(), row.temperatureMaxC(),
                row.bookingStatus(), row.routingStatus(), row.bookedAt(),
                row.routingRequestedAt(), row.lastNotifiedAt(),
                row.returnedToRoutingAt(), row.returnReason(),
                parsePorts(row.routeExcludeUnlocodes()), row.routeDepartFromUnlocode(),
                row.confirmedAt(), row.trackingNumber(), row.trackingIssuedAt(),
                row.updatedAt(), row.updatedBy());
    }

    /**
     * 保存した除外港（カンマ区切り）を読み出す。<b>読み方を 1 か所にする。</b>
     *
     * <p>探索が組む条件と画面に映す条件が別々に解釈すると、片方だけが正しく
     * なる。調整していなければ空リスト（{@code null} を画面へ渡さない）。</p>
     */
    private static List<String> parsePorts(String stored) {
        return stored == null || stored.isBlank()
                ? List.of() : List.of(stored.split(","));
    }
}
