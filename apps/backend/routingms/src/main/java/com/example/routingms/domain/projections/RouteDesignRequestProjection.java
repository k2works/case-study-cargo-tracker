package com.example.routingms.domain.projections;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 経路設計依頼の Read Model（US06 / cross-service、ADR-0009）。
 *
 * <p>bookingms から受信した {@code RouteDesignRequestedEvent} を記録する。
 * 経路設計者のワークベンチ（IT4 / US08）の入力となる経路設計待ちリストを表す。</p>
 */
public class RouteDesignRequestProjection {

    private String bookingId;
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private String status;
    private LocalDateTime requestedAt;

    public RouteDesignRequestProjection() { /* MyBatis result mapping */ }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getOriginUnlocode() { return originUnlocode; }
    public void setOriginUnlocode(String originUnlocode) { this.originUnlocode = originUnlocode; }

    public String getDestinationUnlocode() { return destinationUnlocode; }
    public void setDestinationUnlocode(String destinationUnlocode) { this.destinationUnlocode = destinationUnlocode; }

    public LocalDate getArrivalDeadline() { return arrivalDeadline; }
    public void setArrivalDeadline(LocalDate arrivalDeadline) { this.arrivalDeadline = arrivalDeadline; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
