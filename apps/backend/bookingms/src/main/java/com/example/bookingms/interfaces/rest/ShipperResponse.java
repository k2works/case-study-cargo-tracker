package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.Shipper;

public record ShipperResponse(
        Long id, String shipperCode, String type, String name, String email, String address,
        String phone) implements ShipperRegistrationResponse {

    public static ShipperResponse from(Shipper shipper) {
        return new ShipperResponse(
                shipper.id(), shipper.shipperCode(), shipper.type().name(), shipper.name(),
                shipper.email(), shipper.address(), shipper.phone());
    }
}
