package com.example.bookingms.domain.projections;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 貨物予約 Read Model (POJO + MyBatis ResultMap)。
 *
 * <p>cargo_summary テーブルの各カラムに対応するフィールドを持つ。
 * MyBatis が setter を呼び出して値を設定する。
 * US05 で hazard_* / temperature_* カラムに対応するフィールドを追加。</p>
 */
public class CargoSummary {

    private String bookingId;
    private String shipperId;
    private String trackingNumber;
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private BigDecimal weightKg;
    private Integer lengthCm;
    private Integer widthCm;
    private Integer heightCm;
    private Integer quantity;
    private String productName;
    private String hazardImoClass;
    private String hazardUnNumber;
    private String hazardDeclaration;
    private BigDecimal temperatureMinC;
    private BigDecimal temperatureMaxC;
    private String bookingStatus;
    private String routingStatus;
    private BigDecimal estimatedAmount;
    private String estimatedCurrency;
    private LocalDateTime lastEventAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public CargoSummary() { /* MyBatis result mapping */ }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getOriginUnlocode() { return originUnlocode; }
    public void setOriginUnlocode(String originUnlocode) { this.originUnlocode = originUnlocode; }

    public String getDestinationUnlocode() { return destinationUnlocode; }
    public void setDestinationUnlocode(String destinationUnlocode) { this.destinationUnlocode = destinationUnlocode; }

    public LocalDate getArrivalDeadline() { return arrivalDeadline; }
    public void setArrivalDeadline(LocalDate arrivalDeadline) { this.arrivalDeadline = arrivalDeadline; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public Integer getLengthCm() { return lengthCm; }
    public void setLengthCm(Integer lengthCm) { this.lengthCm = lengthCm; }

    public Integer getWidthCm() { return widthCm; }
    public void setWidthCm(Integer widthCm) { this.widthCm = widthCm; }

    public Integer getHeightCm() { return heightCm; }
    public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getHazardImoClass() { return hazardImoClass; }
    public void setHazardImoClass(String hazardImoClass) { this.hazardImoClass = hazardImoClass; }

    public String getHazardUnNumber() { return hazardUnNumber; }
    public void setHazardUnNumber(String hazardUnNumber) { this.hazardUnNumber = hazardUnNumber; }

    public String getHazardDeclaration() { return hazardDeclaration; }
    public void setHazardDeclaration(String hazardDeclaration) { this.hazardDeclaration = hazardDeclaration; }

    public BigDecimal getTemperatureMinC() { return temperatureMinC; }
    public void setTemperatureMinC(BigDecimal temperatureMinC) { this.temperatureMinC = temperatureMinC; }

    public BigDecimal getTemperatureMaxC() { return temperatureMaxC; }
    public void setTemperatureMaxC(BigDecimal temperatureMaxC) { this.temperatureMaxC = temperatureMaxC; }

    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String bookingStatus) { this.bookingStatus = bookingStatus; }

    public String getRoutingStatus() { return routingStatus; }
    public void setRoutingStatus(String routingStatus) { this.routingStatus = routingStatus; }

    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }

    public String getEstimatedCurrency() { return estimatedCurrency; }
    public void setEstimatedCurrency(String estimatedCurrency) { this.estimatedCurrency = estimatedCurrency; }

    public LocalDateTime getLastEventAt() { return lastEventAt; }
    public void setLastEventAt(LocalDateTime lastEventAt) { this.lastEventAt = lastEventAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
