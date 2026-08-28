package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.valueobjects.ContractNumber;
import com.example.bookingms.domain.model.valueobjects.DiscountRate;
import com.example.bookingms.domain.model.aggregates.Shipper;
import java.math.BigDecimal;

public record ShipperResponse(
        Long id, String shipperCode, String type, String name, String email, String address,
        String phone, String contractNumber, BigDecimal discountRatePercent)
        implements ShipperRegistrationResponse {

    public static ShipperResponse from(Shipper shipper) {
        return new ShipperResponse(
                shipper.id(), shipper.shipperCode(), shipper.type().name(), shipper.name(),
                shipper.email().value(), shipper.address(), shipper.phone(),
                shipper.contractNumber().map(ContractNumber::value).orElse(null),
                shipper.discountRate().map(DiscountRate::percent).orElse(null));
    }
}
