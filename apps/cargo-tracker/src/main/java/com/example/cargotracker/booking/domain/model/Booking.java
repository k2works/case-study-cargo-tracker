package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.event.BookingRegisteredEvent;
import com.example.cargotracker.booking.domain.event.DomainEvent;
import com.example.cargotracker.shared.domain.model.ShipperId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 予約集約ルート。
 */
public class Booking {

    private final BookingId id;
    private final ShipperId shipperId;
    private final CargoSpecification cargoSpecification;
    private final TransportCondition transportCondition;
    private final BookingStatus status;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

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

    /**
     * 永続化ストアから予約を再構成する。ドメインイベントは発行しない。
     */
    public static Booking reconstitute(BookingId id, ShipperId shipperId,
                                       CargoSpecification cargoSpecification,
                                       TransportCondition transportCondition,
                                       BookingStatus status) {
        return new Booking(id, shipperId, cargoSpecification, transportCondition, status);
    }

    public BookingId getId() { return id; }
    public ShipperId getShipperId() { return shipperId; }
    public CargoSpecification getCargoSpecification() { return cargoSpecification; }
    public TransportCondition getTransportCondition() { return transportCondition; }
    public BookingStatus getStatus() { return status; }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
