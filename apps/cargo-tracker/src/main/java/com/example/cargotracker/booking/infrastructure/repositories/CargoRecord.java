package com.example.cargotracker.booking.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CargoRecord {

    private Long id;
    private String bookingId;
    private String shipperId;
    private String cargoType;
    private BigDecimal weight;
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private BigDecimal dimensionLength;
    private BigDecimal dimensionWidth;
    private BigDecimal dimensionHeight;
    private Integer quantity;
    private String description;
    private String bookingStatus;
    private String hazardousClass;
    private String unNumber;
    private String properShippingName;
    private BigDecimal minTemperature;
    private BigDecimal maxTemperature;
    private String temperatureUnit;

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

    public String getShipperId() {
        return shipperId;
    }

    public void setShipperId(String shipperId) {
        this.shipperId = shipperId;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getOriginUnlocode() {
        return originUnlocode;
    }

    public void setOriginUnlocode(String originUnlocode) {
        this.originUnlocode = originUnlocode;
    }

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public void setDestinationUnlocode(String destinationUnlocode) {
        this.destinationUnlocode = destinationUnlocode;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public void setArrivalDeadline(LocalDate arrivalDeadline) {
        this.arrivalDeadline = arrivalDeadline;
    }

    public BigDecimal getDimensionLength() { return dimensionLength; }
    public void setDimensionLength(BigDecimal dimensionLength) { this.dimensionLength = dimensionLength; }

    public BigDecimal getDimensionWidth() { return dimensionWidth; }
    public void setDimensionWidth(BigDecimal dimensionWidth) { this.dimensionWidth = dimensionWidth; }

    public BigDecimal getDimensionHeight() { return dimensionHeight; }
    public void setDimensionHeight(BigDecimal dimensionHeight) { this.dimensionHeight = dimensionHeight; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public String getHazardousClass() { return hazardousClass; }
    public void setHazardousClass(String hazardousClass) { this.hazardousClass = hazardousClass; }

    public String getUnNumber() { return unNumber; }
    public void setUnNumber(String unNumber) { this.unNumber = unNumber; }

    public String getProperShippingName() { return properShippingName; }
    public void setProperShippingName(String properShippingName) { this.properShippingName = properShippingName; }

    public BigDecimal getMinTemperature() { return minTemperature; }
    public void setMinTemperature(BigDecimal minTemperature) { this.minTemperature = minTemperature; }

    public BigDecimal getMaxTemperature() { return maxTemperature; }
    public void setMaxTemperature(BigDecimal maxTemperature) { this.maxTemperature = maxTemperature; }

    public String getTemperatureUnit() { return temperatureUnit; }
    public void setTemperatureUnit(String temperatureUnit) { this.temperatureUnit = temperatureUnit; }
}
