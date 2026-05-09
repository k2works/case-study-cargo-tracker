package com.example.bookingms.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * cargo テーブルの永続化レコード
 */
public class CargoRecord {

    private Long id;
    private String bookingId;
    private Long shipperId;
    private String bookingStatus;
    private String transportStatus;
    private String routingStatus;
    private String cargoType;
    private BigDecimal weightKg;
    private String specOriginUnlocode;
    private String specDestinationUnlocode;
    private LocalDate specArrivalDeadline;
    private Integer bookingAmountValue;
    private String bookingAmountCurrency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String hazmatUnCode;
    private String hazmatHazardClass;
    private String hazmatPackingGroup;
    private Double tempMinCelsius;
    private Double tempMaxCelsius;
    private String tempUnit;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public Long getShipperId() { return shipperId; }
    public void setShipperId(Long shipperId) { this.shipperId = shipperId; }

    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String bookingStatus) { this.bookingStatus = bookingStatus; }

    public String getTransportStatus() { return transportStatus; }
    public void setTransportStatus(String transportStatus) { this.transportStatus = transportStatus; }

    public String getRoutingStatus() { return routingStatus; }
    public void setRoutingStatus(String routingStatus) { this.routingStatus = routingStatus; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public String getSpecOriginUnlocode() { return specOriginUnlocode; }
    public void setSpecOriginUnlocode(String specOriginUnlocode) { this.specOriginUnlocode = specOriginUnlocode; }

    public String getSpecDestinationUnlocode() { return specDestinationUnlocode; }
    public void setSpecDestinationUnlocode(String specDestinationUnlocode) { this.specDestinationUnlocode = specDestinationUnlocode; }

    public LocalDate getSpecArrivalDeadline() { return specArrivalDeadline; }
    public void setSpecArrivalDeadline(LocalDate specArrivalDeadline) { this.specArrivalDeadline = specArrivalDeadline; }

    public Integer getBookingAmountValue() { return bookingAmountValue; }
    public void setBookingAmountValue(Integer bookingAmountValue) { this.bookingAmountValue = bookingAmountValue; }

    public String getBookingAmountCurrency() { return bookingAmountCurrency; }
    public void setBookingAmountCurrency(String bookingAmountCurrency) { this.bookingAmountCurrency = bookingAmountCurrency; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getHazmatUnCode() { return hazmatUnCode; }
    public void setHazmatUnCode(String hazmatUnCode) { this.hazmatUnCode = hazmatUnCode; }

    public String getHazmatHazardClass() { return hazmatHazardClass; }
    public void setHazmatHazardClass(String hazmatHazardClass) { this.hazmatHazardClass = hazmatHazardClass; }

    public String getHazmatPackingGroup() { return hazmatPackingGroup; }
    public void setHazmatPackingGroup(String hazmatPackingGroup) { this.hazmatPackingGroup = hazmatPackingGroup; }

    public Double getTempMinCelsius() { return tempMinCelsius; }
    public void setTempMinCelsius(Double tempMinCelsius) { this.tempMinCelsius = tempMinCelsius; }

    public Double getTempMaxCelsius() { return tempMaxCelsius; }
    public void setTempMaxCelsius(Double tempMaxCelsius) { this.tempMaxCelsius = tempMaxCelsius; }

    public String getTempUnit() { return tempUnit; }
    public void setTempUnit(String tempUnit) { this.tempUnit = tempUnit; }
}
