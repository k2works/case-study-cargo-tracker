package com.example.bookingms.domain.model.aggregates;

import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;

import java.util.Objects;

/**
 * 貨物（Cargo）集約ルート
 */
public class Cargo {

    private Long id;
    private final BookingId bookingId;
    private final Long shipperId;
    private BookingStatus bookingStatus;
    private final CargoType cargoType;
    private final Weight weight;
    private RouteSpecification routeSpecification;

    /**
     * 新規貨物作成コンストラクタ
     */
    public Cargo(BookingId bookingId, Long shipperId, CargoType cargoType,
                 Weight weight, RouteSpecification routeSpecification) {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(shipperId, "shipperId must not be null");
        Objects.requireNonNull(cargoType, "cargoType must not be null");
        Objects.requireNonNull(weight, "weight must not be null");
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.cargoType = cargoType;
        this.weight = weight;
        this.routeSpecification = routeSpecification;
        this.bookingStatus = BookingStatus.PRELIMINARY;
    }

    /**
     * 永続化済み貨物再構成コンストラクタ
     */
    public Cargo(Long id, BookingId bookingId, Long shipperId, BookingStatus bookingStatus,
                 CargoType cargoType, Weight weight, RouteSpecification routeSpecification) {
        this(bookingId, shipperId, cargoType, weight, routeSpecification);
        this.id = id;
        this.bookingStatus = bookingStatus;
    }

    public Long getId() {
        return id;
    }

    public BookingId getBookingId() {
        return bookingId;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public BookingStatus getBookingStatus() {
        return bookingStatus;
    }

    public CargoType getCargoType() {
        return cargoType;
    }

    public Weight getWeight() {
        return weight;
    }

    public RouteSpecification getRouteSpecification() {
        return routeSpecification;
    }

    /**
     * 経路仕様を更新する
     */
    public void specifyRoute(RouteSpecification routeSpecification) {
        Objects.requireNonNull(routeSpecification, "routeSpecification must not be null");
        this.routeSpecification = routeSpecification;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cargo cargo = (Cargo) o;
        return Objects.equals(bookingId, cargo.bookingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookingId);
    }

    @Override
    public String toString() {
        return "Cargo{bookingId=" + bookingId + ", status=" + bookingStatus + '}';
    }
}
