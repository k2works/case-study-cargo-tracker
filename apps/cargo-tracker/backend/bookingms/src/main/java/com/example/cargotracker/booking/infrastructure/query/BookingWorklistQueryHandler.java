package com.example.cargotracker.booking.infrastructure.query;

import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingConfirmationListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingConfirmationView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingTrackingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingTrackingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ConditionReviewListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ConditionReviewView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountAwaitingNotificationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountBookingsByStatusQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindAwaitingConfirmationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindAwaitingTrackingNumberQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindConditionReviewsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/**
 * ロールごとの「受け皿」と件数（S02・S30）。
 *
 * <p>{@link BookingQueryHandler} から切り出した。<b>予約 1 件を読むのとは関心が違う</b>
 * ——こちらは「その人がいま何をすべきか」を集める読み口で、対象は複数件である。</p>
 *
 * <p><b>件数だけを返さない読み口がある。</b> 件数では、どの予約を開けばよいかが
 * 分からない（IT4 の「気づく手段は次の行動へ繋ぐ」）。</p>
 *
 * <p>分けた直接のきっかけは、{@code BookingQueryHandler} の依存が 23 個になり
 * SonarQube が「割る基準を決めていない」と指した IT7 のクローズである。</p>
 */
@Component
public class BookingWorklistQueryHandler {

    private final CargoSummaryMapper cargos;

    public BookingWorklistQueryHandler(CargoSummaryMapper cargos) {
        this.cargos = cargos;
    }

    /** 見直しを頼まれている予約（S02 / 営業。US10 §4）。古い依頼から順に返す。 */
    @QueryHandler
    public ConditionReviewListView handle(FindConditionReviewsQuery query) {
        return new ConditionReviewListView(cargos.findConditionReviews(query.limit()).stream()
                .map(row -> new ConditionReviewView(row.bookingId(), row.bookingNumber(),
                        row.reason(), row.requestedAt()))
                .toList());
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
}
