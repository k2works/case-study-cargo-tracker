package com.example.cargotracker.booking.domain;

import com.example.cargotracker.shipper.domain.ShipperId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 予約集約ルート。
 */
public class Booking {

    private final BookingId id;
    private final ShipperId shipperId;
    private final CargoSpecification cargoSpecification;
    private final TransportCondition transportCondition;
    private final BookingStatus status;
    private final List<Object> domainEvents = new ArrayList<>();

    private Booking(BookingId id, ShipperId shipperId,
                    CargoSpecification cargoSpecification,
                    TransportCondition transportCondition,
                    BookingStatus status) {
        this.id = id;
        this.shipperId = shipperId;
        this.cargoSpecification = cargoSpecification;
        this.transportCondition = transportCondition;
        this.status = status;
    }

    /**
     * 予約を登録する。
     */
    public static Booking register(BookingId id, ShipperId shipperId,
                                   CargoSpecification cargoSpecification,
                                   TransportCondition transportCondition) {
        if (id == null) throw new IllegalArgumentException("予約 ID は null にできません");
        if (shipperId == null) throw new IllegalArgumentException("荷主 ID は null にできません");
        if (cargoSpecification == null) throw new IllegalArgumentException("貨物仕様は null にできません");
        if (transportCondition == null) throw new IllegalArgumentException("輸送条件は null にできません");

        Booking booking = new Booking(id, shipperId, cargoSpecification, transportCondition, BookingStatus.PROVISIONAL);
        booking.domainEvents.add(new BookingRegisteredEvent(id, shipperId));
        return booking;
    }

    public BookingId getId() { return id; }
    public ShipperId getShipperId() { return shipperId; }
    public CargoSpecification getCargoSpecification() { return cargoSpecification; }
    public TransportCondition getTransportCondition() { return transportCondition; }
    public BookingStatus getStatus() { return status; }

    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
