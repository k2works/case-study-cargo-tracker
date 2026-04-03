package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.event.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.event.BookingRegisteredEvent;
import com.example.cargotracker.booking.domain.event.BookingRouteAssignedEvent;
import com.example.cargotracker.booking.domain.event.DomainEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
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
    private BookingStatus status;          // 非 final: US08 confirm() で変化する
    private AssignedRoute assignedRoute;   // null = 未割り当て
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Booking(BookingId id, ShipperId shipperId,
                    CargoSpecification cargoSpecification,
                    TransportCondition transportCondition,
                    BookingStatus status,
                    AssignedRoute assignedRoute) {
        this.id = id;
        this.shipperId = shipperId;
        this.cargoSpecification = cargoSpecification;
        this.transportCondition = transportCondition;
        this.status = status;
        this.assignedRoute = assignedRoute;
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

        Booking booking = new Booking(id, shipperId, cargoSpecification, transportCondition,
                BookingStatus.PROVISIONAL, null);
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
        return new Booking(id, shipperId, cargoSpecification, transportCondition, status, null);
    }

    /**
     * 永続化ストアから予約を再構成する（ルート情報含む）。
     */
    public static Booking reconstitute(BookingId id, ShipperId shipperId,
                                       CargoSpecification cargoSpecification,
                                       TransportCondition transportCondition,
                                       BookingStatus status,
                                       AssignedRoute assignedRoute) {
        return new Booking(id, shipperId, cargoSpecification, transportCondition, status, assignedRoute);
    }

    /**
     * ルートを割り当てる。
     */
    public void assignRoute(AssignedRoute assignedRoute) {
        if (assignedRoute == null) throw new IllegalArgumentException("割り当てルートは null にできません");
        this.assignedRoute = assignedRoute;
        this.domainEvents.add(new BookingRouteAssignedEvent(id, assignedRoute));
    }

    /**
     * 予約を確定する。ルートが未割り当ての場合は例外を投げる。
     */
    public void confirm() {
        if (assignedRoute == null) {
            throw new IllegalStateException("ルートが割り当てられていない予約は確定できません");
        }
        if (status == BookingStatus.CONFIRMED) {
            throw new IllegalStateException("既に確定済みの予約です");
        }
        this.status = BookingStatus.CONFIRMED;
        this.domainEvents.add(new BookingConfirmedEvent(id));
    }

    /**
     * 精算を完了する。
     */
    public void settle() {
        if (status == BookingStatus.SETTLED) {
            throw new IllegalStateException("既に精算済みの予約です");
        }
        if (status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("確定済みの予約のみ精算済みにできます");
        }
        this.status = BookingStatus.SETTLED;
    }

    public BookingId getId() { return id; }
    public ShipperId getShipperId() { return shipperId; }
    public CargoSpecification getCargoSpecification() { return cargoSpecification; }
    public TransportCondition getTransportCondition() { return transportCondition; }
    public BookingStatus getStatus() { return status; }
    public AssignedRoute getAssignedRoute() { return assignedRoute; }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
