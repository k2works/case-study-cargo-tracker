package com.example.cargotracker.bookingms.interfaces.events;

import com.example.cargotracker.bookingms.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.bookingms.domain.model.events.CargoHandedOffToRoutingEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryRecord;
import com.example.cargotracker.shared.events.CargoBookedEvent;
import com.example.cargotracker.shared.events.CargoRoutedEvent;
import com.example.cargotracker.shared.events.TrackingNumberIssuedEvent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * CargoBookedEvent 等を受け取り、cargo_summary Read Model を更新する EventHandler。
 *
 * <p>ADR-0007 採用パターン: Axon Server の Event Bus から購読し、
 * MyBatis Mapper 経由で cargo_summary テーブルを更新する。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class CargoProjectionsEventHandler {

    private final CargoSummaryMapper cargoSummaryMapper;

    public CargoProjectionsEventHandler(CargoSummaryMapper cargoSummaryMapper) {
        this.cargoSummaryMapper = cargoSummaryMapper;
    }

    @EventHandler
    @Transactional
    public void on(CargoBookedEvent event) {
        var summary = new CargoSummaryRecord();
        summary.setBookingId(event.bookingId());
        summary.setShipperId(event.shipperId() != null ? Long.parseLong(event.shipperId()) : null);
        summary.setCargoType(event.cargoType());
        summary.setWeightKg(event.weightKg());
        summary.setLengthCm(event.lengthCm());
        summary.setWidthCm(event.widthCm());
        summary.setHeightCm(event.heightCm());
        summary.setQuantity(event.quantity());
        summary.setProductName(event.productName());
        summary.setHazardImoClass(event.hazardImoClass());
        summary.setHazardUnNumber(event.hazardUnNumber());
        summary.setHazardDeclaration(event.hazardDeclaration());
        summary.setTemperatureMinC(event.temperatureMinC());
        summary.setTemperatureMaxC(event.temperatureMaxC());
        summary.setOriginUnlocode(event.originUnlocode());
        summary.setDestinationUnlocode(event.destinationUnlocode());
        summary.setArrivalDeadline(event.arrivalDeadline());
        summary.setBookingStatus(BookingStatus.PRELIMINARY.name());
        summary.setRoutingStatus(RoutingStatus.NOT_ROUTED.name());
        cargoSummaryMapper.insert(summary);
    }

    @EventHandler
    @Transactional
    public void on(CargoHandedOffToRoutingEvent event) {
        cargoSummaryMapper.updateBookingStatus(event.bookingId(), BookingStatus.ROUTING.name());
    }

    @EventHandler
    @Transactional
    public void on(CargoRoutedEvent event) {
        cargoSummaryMapper.updateBookingStatus(event.bookingId(), BookingStatus.ROUTE_PROPOSED.name());
    }

    @EventHandler
    @Transactional
    public void on(BookingConfirmedEvent event) {
        cargoSummaryMapper.updateBookingStatus(event.bookingId(), BookingStatus.CONFIRMED.name());
    }

    @EventHandler
    @Transactional
    public void on(TrackingNumberIssuedEvent event) {
        cargoSummaryMapper.updateTrackingNumber(event.bookingId(), event.trackingNumber());
        cargoSummaryMapper.updateBookingStatus(event.bookingId(), BookingStatus.TRACKING_ISSUED.name());
    }
}
