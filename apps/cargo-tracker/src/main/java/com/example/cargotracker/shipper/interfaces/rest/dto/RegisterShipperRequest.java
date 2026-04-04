package com.example.cargotracker.shipper.interfaces.rest.dto;

import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class RegisterShipperRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private String phone;

    @NotNull
    private ShipperType shipperType;

    private String contractNumber;

    @DecimalMin("0.0")
    @DecimalMax("0.15")
    private BigDecimal discountRate;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public ShipperType getShipperType() {
        return shipperType;
    }

    public void setShipperType(ShipperType shipperType) {
        this.shipperType = shipperType;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    @AssertTrue(message = "法人荷主には契約番号が必要です。")
    public boolean isContractNumberValid() {
        return shipperType != ShipperType.CORPORATE
                || (contractNumber != null && !contractNumber.isBlank());
    }

    @AssertTrue(message = "法人荷主には割引率が必要です。")
    public boolean isDiscountRateValid() {
        return shipperType != ShipperType.CORPORATE || discountRate != null;
    }
}
