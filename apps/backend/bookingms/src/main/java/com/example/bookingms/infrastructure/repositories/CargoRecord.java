package com.example.bookingms.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

/** cargo テーブルの 1 行。 */
public class CargoRecord {

    private Long id;
    private String bookingId;
    private Long shipperId;
    private String bookingStatus;
    private String transportStatus;
    private String routingStatus;
    private String cargoType;
    private BigDecimal weightKg;
    private Integer quantity;
    private String description;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
    private String specOriginUnlocode;
    private String specOriginName;
    private String specDestinationUnlocode;
    private String specDestinationName;
    private LocalDate specArrivalDeadline;
    private LocalDate specDepartureDate;
    private String hazardousClass;
    private String unNumber;
    private String properShippingName;
    private BigDecimal tempMin;
    private BigDecimal tempMax;
    private String tempUnit;
    /** 荷主へ通知した日時（US12-4）。通知していなければ {@code null}。 */
    private java.time.Instant routeNotifiedAt;

    /** 荷主へ通知した担当者（US12-4）。 */
    private String routeNotifiedBy;

    /** 発行済みの追跡番号（US14）。未発行なら {@code null}。 */
    private String trackingNumber;

    /** 最後に荷役があった地点（[ADR-025] 決定 4）。まだ無ければ null。 */
    private String lastHandlingLocationUnlocode;

    /** 最後の荷役の日時。まだ無ければ null。 */
    private java.time.Instant lastHandlingAt;

    /** 誤配が起きた日時（US28・[ADR-026] 決定 3）。**再設計しても消さない**。 */
    private java.time.Instant misroutedAt;

    /** 誤配が起きた港。「誤配があった」だけでは荷主にも経理にも説明できない。 */
    private String misroutedLocationUnlocode;

    private String shipperName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }

    public String getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public String getTransportStatus() {
        return transportStatus;
    }

    public void setTransportStatus(String transportStatus) {
        this.transportStatus = transportStatus;
    }

    public String getRoutingStatus() {
        return routingStatus;
    }

    public void setRoutingStatus(String routingStatus) {
        this.routingStatus = routingStatus;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(BigDecimal widthCm) {
        this.widthCm = widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public String getSpecOriginUnlocode() {
        return specOriginUnlocode;
    }

    public void setSpecOriginUnlocode(String specOriginUnlocode) {
        this.specOriginUnlocode = specOriginUnlocode;
    }

    public String getSpecOriginName() {
        return specOriginName;
    }

    public void setSpecOriginName(String specOriginName) {
        this.specOriginName = specOriginName;
    }

    public String getSpecDestinationUnlocode() {
        return specDestinationUnlocode;
    }

    public void setSpecDestinationUnlocode(String specDestinationUnlocode) {
        this.specDestinationUnlocode = specDestinationUnlocode;
    }

    public String getSpecDestinationName() {
        return specDestinationName;
    }

    public void setSpecDestinationName(String specDestinationName) {
        this.specDestinationName = specDestinationName;
    }

    public LocalDate getSpecArrivalDeadline() {
        return specArrivalDeadline;
    }

    public void setSpecArrivalDeadline(LocalDate specArrivalDeadline) {
        this.specArrivalDeadline = specArrivalDeadline;
    }

    public LocalDate getSpecDepartureDate() {
        return specDepartureDate;
    }

    public void setSpecDepartureDate(LocalDate specDepartureDate) {
        this.specDepartureDate = specDepartureDate;
    }

    public String getHazardousClass() {
        return hazardousClass;
    }

    public void setHazardousClass(String hazardousClass) {
        this.hazardousClass = hazardousClass;
    }

    public String getUnNumber() {
        return unNumber;
    }

    public void setUnNumber(String unNumber) {
        this.unNumber = unNumber;
    }

    public String getProperShippingName() {
        return properShippingName;
    }

    public void setProperShippingName(String properShippingName) {
        this.properShippingName = properShippingName;
    }

    public BigDecimal getTempMin() {
        return tempMin;
    }

    public void setTempMin(BigDecimal tempMin) {
        this.tempMin = tempMin;
    }

    public BigDecimal getTempMax() {
        return tempMax;
    }

    public void setTempMax(BigDecimal tempMax) {
        this.tempMax = tempMax;
    }

    public String getTempUnit() {
        return tempUnit;
    }

    public void setTempUnit(String tempUnit) {
        this.tempUnit = tempUnit;
    }

    /** 一覧で荷主名を出すために結合して取る。予約番号だけでは誰の貨物か分からない。 */
    public String getShipperName() {
        return shipperName;
    }

    public java.time.Instant getRouteNotifiedAt() {
        return routeNotifiedAt;
    }

    public void setRouteNotifiedAt(java.time.Instant routeNotifiedAt) {
        this.routeNotifiedAt = routeNotifiedAt;
    }

    public String getRouteNotifiedBy() {
        return routeNotifiedBy;
    }

    public void setRouteNotifiedBy(String routeNotifiedBy) {
        this.routeNotifiedBy = routeNotifiedBy;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getLastHandlingLocationUnlocode() {
        return lastHandlingLocationUnlocode;
    }

    public void setLastHandlingLocationUnlocode(String lastHandlingLocationUnlocode) {
        this.lastHandlingLocationUnlocode = lastHandlingLocationUnlocode;
    }

    public java.time.Instant getLastHandlingAt() {
        return lastHandlingAt;
    }

    public void setLastHandlingAt(java.time.Instant lastHandlingAt) {
        this.lastHandlingAt = lastHandlingAt;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }
    public java.time.Instant getMisroutedAt() {
        return misroutedAt;
    }

    public void setMisroutedAt(java.time.Instant misroutedAt) {
        this.misroutedAt = misroutedAt;
    }

    public String getMisroutedLocationUnlocode() {
        return misroutedLocationUnlocode;
    }

    public void setMisroutedLocationUnlocode(String misroutedLocationUnlocode) {
        this.misroutedLocationUnlocode = misroutedLocationUnlocode;
    }

}
