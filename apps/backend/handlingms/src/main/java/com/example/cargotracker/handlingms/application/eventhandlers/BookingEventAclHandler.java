package com.example.cargotracker.handlingms.application.eventhandlers;

import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotRecord;
import com.example.cargotracker.shared.events.CargoBookedEvent;
import com.example.cargotracker.shared.events.CargoRoutedEvent;
import com.example.cargotracker.shared.events.TrackingNumberIssuedEvent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * bookingms から Axon Event Bus 経由で受け取った Event を cargo_snapshot に反映する ACL。
 *
 * <p>ADR-0012 / ADR-0014 に基づき、handlingms は bookingms に直接依存せず、
 * shared.events パッケージの Event クラスのみを参照する。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class BookingEventAclHandler {

    private final CargoSnapshotMapper mapper;

    public BookingEventAclHandler(CargoSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @EventHandler
    @Transactional
    public void on(CargoBookedEvent event) {
        var snapshot = new CargoSnapshotRecord();
        snapshot.setBookingId(event.bookingId());
        snapshot.setOriginUnlocode(event.originUnlocode());
        snapshot.setDestinationUnlocode(event.destinationUnlocode());
        snapshot.setCargoType(event.cargoType());
        snapshot.setArrivalDeadline(event.arrivalDeadline());
        snapshot.setBookingStatus("PRELIMINARY");
        mapper.upsert(snapshot);
    }

    @EventHandler
    @Transactional
    public void on(CargoRoutedEvent event) {
        mapper.updateBookingStatusByBookingId(event.bookingId(), "ROUTE_PROPOSED");
    }

    @EventHandler
    @Transactional
    public void on(TrackingNumberIssuedEvent event) {
        mapper.updateTrackingNumber(event.bookingId(), event.trackingNumber(), "TRACKING_ISSUED");
    }
}
