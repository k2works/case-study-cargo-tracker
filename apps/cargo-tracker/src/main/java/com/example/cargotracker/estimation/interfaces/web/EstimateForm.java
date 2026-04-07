package com.example.cargotracker.estimation.interfaces.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class EstimateForm {

    @NotBlank(message = "出発地を入力してください")
    @Size(min = 5, max = 5, message = "UN/LOCODE は 5 文字で入力してください")
    private String originUnlocode;

    @NotBlank(message = "目的地を入力してください")
    @Size(min = 5, max = 5, message = "UN/LOCODE は 5 文字で入力してください")
    private String destinationUnlocode;

    @NotNull(message = "希望期限を入力してください")
    private LocalDate arrivalDeadline;

    @NotBlank(message = "貨物種別を選択してください")
    private String cargoType;

    @NotNull(message = "重量を入力してください")
    @DecimalMin(value = "0.001", message = "重量は 0 より大きい値を入力してください")
    private BigDecimal weightKg;

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
}
