package com.example.cargotracker.booking.infrastructure.query;

import com.example.cargotracker.booking.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountBookingsByStatusQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 予約の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class BookingQueryHandler {

    private final CargoSummaryMapper cargos;

    public BookingQueryHandler(CargoSummaryMapper cargos) {
        this.cargos = cargos;
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
                row.routingRequestedAt());
    }
}
