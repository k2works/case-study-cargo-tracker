package com.example.cargotracker.booking.infrastructure.query;

import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingItineraryQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingNotificationsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindShipperBookingProgressQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindShipperBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ShipperBookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ShipperBookingProgressView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ShipperBookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ShipperNotificationView;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/**
 * 荷主向けの予約の問い合わせ（S45・S46 / US18・US37）。
 *
 * <p><b>社内向けと同じクラスに置かない。</b> 荷主に出すのは「どこからどこへ・
 * いつまでに・いまどうなっているか」だけで、金額の材料・社内メモ・担当者名は
 * 出さない——同じ場所に置くと、列を足した誰かが荷主向けにも見せるつもりの
 * ない項目を静かに増やす。</p>
 *
 * <p><b>旅程と通知は社内向けの読み口から借りる</b>（写しを作らない）。
 * 落とすのは「荷主に見せない項目」だけで、読み方そのものは 1 か所に置く。</p>
 */
@Component
public class ShipperBookingQueryHandler {

    private final CargoSummaryMapper cargos;
    private final BookingQueryHandler bookings;

    public ShipperBookingQueryHandler(CargoSummaryMapper cargos, BookingQueryHandler bookings) {
        this.cargos = cargos;
        this.bookings = bookings;
    }

    /** 自社予約一覧（S45）。<b>荷主で絞るのはサーバ</b>。 */
    @QueryHandler
    public ShipperBookingListView handle(FindShipperBookingsQuery query) {
        int limit = Math.clamp(query.limit(), 1, 200);
        return new ShipperBookingListView(
                cargos.findByShipper(query.shipperId(), query.includeFinished(), limit).stream()
                        .map(ShipperBookingQueryHandler::toShipperView).toList(),
                cargos.countByShipper(query.shipperId(), query.includeFinished()));
    }

    /**
     * 自社予約の進み具合（S46）。
     *
     * <p><b>自社のものでなければ返さない。</b> 予約 ID は推測できるし共有もされる
     * ので、「見つからない」と「他社のもの」を画面には区別させない（どちらも
     * {@code null}）。</p>
     */
    @QueryHandler
    public ShipperBookingProgressView handle(FindShipperBookingProgressQuery query) {
        CargoSummaryMapper.CargoSummaryRow row = cargos.findById(query.bookingId());
        if (row == null || !query.shipperId().equals(row.shipperId())) {
            return null;
        }
        return new ShipperBookingProgressView(
                row.bookingId(), row.bookingNumber(),
                row.originUnlocode(), row.destinationUnlocode(), row.arrivalDeadline(),
                row.cargoType(), row.productName(),
                row.bookingStatus(), row.routingStatus(), row.bookedAt(),
                row.routingRequestedAt(), row.lastNotifiedAt(),
                row.confirmedAt(), row.trackingNumber(), row.trackingIssuedAt(),
                bookings.handle(new FindBookingItineraryQuery(query.bookingId())).legs(),
                bookings.handle(new FindBookingNotificationsQuery(query.bookingId()))
                        .items().stream()
                        // **担当者名と宛先は落とす**（ui_design.md「S46」）。
                        // 荷主に要るのは「いつ・何を伝えられたか」だけである。
                        .map(n -> new ShipperNotificationView(n.notifiedAt(), n.summary()))
                        .toList());
    }

    /**
     * 投影の行を荷主向けの形へ。<b>社内向けの {@code toView} を使い回さない</b>
     * ——荷主に見せない項目（金額の材料・社内メモ・担当者名）を運ばせない。
     */
    static ShipperBookingView toShipperView(CargoSummaryMapper.CargoSummaryRow row) {
        return new ShipperBookingView(
                row.bookingId(), row.bookingNumber(),
                row.originUnlocode(), row.destinationUnlocode(), row.arrivalDeadline(),
                row.productName(), row.bookingStatus(), row.trackingNumber());
    }
}
