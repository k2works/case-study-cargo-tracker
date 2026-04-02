package com.example.cargotracker.routing.interfaces.web.dto;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingSnapshot;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ルート検索フォームの入力値を保持する DTO。
 *
 * <p>予約 ID 起点の検索時は {@link #from(BookingSnapshot)} で初期化し、
 * 代替条件での再検索フォームにも利用する。
 */
public class RoutingSearchForm {

    private String originLocode;
    private String destinationLocode;
    private LocalDate requestedArrivalDate;
    private CargoType cargoType;
    private BigDecimal weightKg;

    public RoutingSearchForm() {}

    public RoutingSearchForm(String originLocode,
                             String destinationLocode,
                             LocalDate requestedArrivalDate,
                             CargoType cargoType,
                             BigDecimal weightKg) {
        this.originLocode = originLocode;
        this.destinationLocode = destinationLocode;
        this.requestedArrivalDate = requestedArrivalDate;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
    }

    public static RoutingSearchForm from(BookingSnapshot snapshot) {
        return new RoutingSearchForm(
                snapshot.originLocode(),
                snapshot.destinationLocode(),
                snapshot.requestedArrivalDate(),
                snapshot.cargoType(),
                snapshot.weightKg()
        );
    }

    public RouteSearchQuery toQuery() {
        return new RouteSearchQuery(
                originLocode, destinationLocode, requestedArrivalDate, cargoType, weightKg);
    }

    public String getOriginLocode() { return originLocode; }
    public void setOriginLocode(String originLocode) { this.originLocode = originLocode; }

    public String getDestinationLocode() { return destinationLocode; }
    public void setDestinationLocode(String destinationLocode) { this.destinationLocode = destinationLocode; }

    public LocalDate getRequestedArrivalDate() { return requestedArrivalDate; }
    public void setRequestedArrivalDate(LocalDate requestedArrivalDate) { this.requestedArrivalDate = requestedArrivalDate; }

    public CargoType getCargoType() { return cargoType; }
    public void setCargoType(CargoType cargoType) { this.cargoType = cargoType; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
}
