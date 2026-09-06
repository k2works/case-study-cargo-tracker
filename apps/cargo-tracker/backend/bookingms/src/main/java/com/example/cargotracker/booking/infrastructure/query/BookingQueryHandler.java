package com.example.cargotracker.booking.infrastructure.query;

import com.example.cargotracker.booking.infrastructure.persistence.CargoLegMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoRevisionMapper;
import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AffectedBookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AffectedBookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
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
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 予約の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class BookingQueryHandler {

    private final CargoSummaryMapper cargos;
    private final CargoRevisionMapper revisions;
    private final CargoLegMapper legs;

    public BookingQueryHandler(CargoSummaryMapper cargos, CargoRevisionMapper revisions,
            CargoLegMapper legs) {
        this.cargos = cargos;
        this.revisions = revisions;
        this.legs = legs;
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
                row.routingRequestedAt(), row.updatedAt(), row.updatedBy());
    }
}
