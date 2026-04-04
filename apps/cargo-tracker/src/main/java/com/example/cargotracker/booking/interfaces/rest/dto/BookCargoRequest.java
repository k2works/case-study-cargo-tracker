package com.example.cargotracker.booking.interfaces.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BookCargoRequest {

    @NotBlank
    private String shipperId;

    @NotBlank
    private String cargoType;

    @NotNull
    @DecimalMin("0.001")
    private BigDecimal weight;

    private BigDecimal dimensionLength;
    private BigDecimal dimensionWidth;
    private BigDecimal dimensionHeight;
    private Integer quantity;
    private String description;

    @NotBlank
    @Size(min = 5, max = 5)
    private String originUnlocode;

    @NotBlank
    @Size(min = 5, max = 5)
    private String destinationUnlocode;

    @NotNull
    @Future
    private LocalDate arrivalDeadline;

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
}
