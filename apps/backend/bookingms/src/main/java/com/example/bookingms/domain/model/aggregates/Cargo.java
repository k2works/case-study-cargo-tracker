package com.example.bookingms.domain.model.aggregates;

import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.HazmatInfo;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.TemperatureInfo;
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
    private CargoItinerary cargoItinerary;
    private final HazmatInfo hazmatInfo;
    private final TemperatureInfo temperatureInfo;

    /**
     * 新規貨物作成コンストラクタ
     */
    public Cargo(BookingId bookingId, Long shipperId, CargoType cargoType,
                 Weight weight, RouteSpecification routeSpecification) {
        this(bookingId, shipperId, cargoType, weight, routeSpecification, null, null);
    }

    /**
     * 新規貨物作成コンストラクタ（特別情報あり）
     */
    public Cargo(BookingId bookingId, Long shipperId, CargoType cargoType,
                 Weight weight, RouteSpecification routeSpecification,
                 HazmatInfo hazmatInfo, TemperatureInfo temperatureInfo) {
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
        this.hazmatInfo = hazmatInfo;
        this.temperatureInfo = temperatureInfo;
    }

    /**
     * 永続化済み貨物再構成コンストラクタ
     */
    public Cargo(Long id, BookingId bookingId, Long shipperId, BookingStatus bookingStatus,
                 CargoType cargoType, Weight weight, RouteSpecification routeSpecification) {
        this(bookingId, shipperId, cargoType, weight, routeSpecification, null, null);
        this.id = id;
        this.bookingStatus = bookingStatus;
    }

    /**
     * 永続化済み貨物再構成コンストラクタ（特別情報あり）
     */
    public Cargo(Long id, BookingId bookingId, Long shipperId, BookingStatus bookingStatus,
                 CargoType cargoType, Weight weight, RouteSpecification routeSpecification,
                 HazmatInfo hazmatInfo, TemperatureInfo temperatureInfo) {
        this(bookingId, shipperId, cargoType, weight, routeSpecification, hazmatInfo, temperatureInfo);
        this.id = id;
        this.bookingStatus = bookingStatus;
    }

    /**
     * 永続化済み貨物再構成コンストラクタ（CargoItinerary あり）
     */
    public Cargo(Long id, BookingId bookingId, Long shipperId, BookingStatus bookingStatus,
                 CargoType cargoType, Weight weight, RouteDetails routeDetails) {
        this(bookingId, shipperId, cargoType, weight, routeDetails.routeSpecification(), null, null);
        this.id = id;
        this.bookingStatus = bookingStatus;
        this.cargoItinerary = routeDetails.cargoItinerary();
    }

    /**
     * 永続化済み貨物再構成コンストラクタ（CargoItinerary + 特別情報あり）
     */
    public Cargo(Long id, BookingId bookingId, Long shipperId, BookingStatus bookingStatus,
                 CargoType cargoType, Weight weight, RouteDetails routeDetails,
                 HazmatInfo hazmatInfo, TemperatureInfo temperatureInfo) {
        this(bookingId, shipperId, cargoType, weight, routeDetails.routeSpecification(), hazmatInfo, temperatureInfo);
        this.id = id;
        this.bookingStatus = bookingStatus;
        this.cargoItinerary = routeDetails.cargoItinerary();
    }

    public record RouteDetails(RouteSpecification routeSpecification, CargoItinerary cargoItinerary) {
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

    public CargoItinerary getCargoItinerary() {
        return cargoItinerary;
    }

    public HazmatInfo getHazmatInfo() {
        return hazmatInfo;
    }

    public TemperatureInfo getTemperatureInfo() {
        return temperatureInfo;
    }

    /**
     * 経路条件を再設定する
     * CONFIRMED 以降の状態では変更不可。旅程をクリアして PRELIMINARY に戻す
     */
    public void updateRouteSpec(RouteSpecification routeSpecification) {
        Objects.requireNonNull(routeSpecification, "routeSpecification must not be null");
        if (this.bookingStatus == BookingStatus.CONFIRMED
                || this.bookingStatus == BookingStatus.TRACKING_ISSUED
                || this.bookingStatus == BookingStatus.IN_TRANSIT
                || this.bookingStatus == BookingStatus.DELIVERED
                || this.bookingStatus == BookingStatus.SETTLED) {
            throw new IllegalStateException(
                    "Cannot update route spec for cargo in status: " + this.bookingStatus);
        }
        this.routeSpecification = routeSpecification;
        this.cargoItinerary = null;
        this.bookingStatus = BookingStatus.PRELIMINARY;
    }

    /**
     * 経路仕様を更新する
     */
    public void specifyRoute(RouteSpecification routeSpecification) {
        Objects.requireNonNull(routeSpecification, "routeSpecification must not be null");
        this.routeSpecification = routeSpecification;
    }

    /**
     * 経路を割り当てる（RouteCargoCommand）
     * CargoItinerary を設定し、予約状態を ROUTE_PROPOSED に遷移させる
     */
    public void assignRoute(CargoItinerary itinerary) {
        Objects.requireNonNull(itinerary, "itinerary must not be null");
        this.cargoItinerary = itinerary;
        this.bookingStatus = BookingStatus.ROUTE_PROPOSED;
    }

    /**
     * 予約を確定する（UpdateBookingStatusCommand）
     * ROUTE_PROPOSED のときのみ CONFIRMED に遷移できる
     */
    public void confirm() {
        if (this.bookingStatus != BookingStatus.ROUTE_PROPOSED) {
            throw new IllegalStateException(
                    "Cannot confirm cargo that is not in ROUTE_PROPOSED state: " + this.bookingStatus);
        }
        this.bookingStatus = BookingStatus.CONFIRMED;
    }

    /**
     * 追跡番号発行済みに遷移する
     * CONFIRMED のときのみ TRACKING_ISSUED に遷移できる
     */
    public void markTrackingIssued() {
        if (this.bookingStatus != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Cannot mark tracking issued for cargo that is not in CONFIRMED state: " + this.bookingStatus);
        }
        this.bookingStatus = BookingStatus.TRACKING_ISSUED;
    }

    /**
     * 予約をキャンセルする
     * DELIVERED または SETTLED 状態の場合はキャンセル不可
     */
    public void cancel() {
        if (this.bookingStatus == BookingStatus.DELIVERED
                || this.bookingStatus == BookingStatus.SETTLED) {
            throw new IllegalStateException(
                    "Cannot cancel cargo in status: " + this.bookingStatus);
        }
        this.bookingStatus = BookingStatus.CANCELLED;
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
