package com.example.cargotracker.shipper.interfaces.rest.dto;

import java.math.BigDecimal;

public class ShipperResponse {

    private final String id;
    private final String code;
    private final String name;
    private final String email;
    private final String phone;
    private final String shipperType;
    private final String contractNumber;
    private final BigDecimal discountRate;

    public ShipperResponse(
            String id,
            String code,
            String name,
            String email,
            String phone,
            String shipperType,
            String contractNumber,
            BigDecimal discountRate
    ) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.shipperType = shipperType;
        this.contractNumber = contractNumber;
        this.discountRate = discountRate;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getShipperType() {
        return shipperType;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }
}
