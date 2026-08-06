package com.example.cargotracker.booking.domain.model.exceptions;

import com.example.cargotracker.shared.domain.model.ShipperId;

public class ShipperNotFoundException extends RuntimeException {

    private final transient ShipperId shipperId;

    public ShipperNotFoundException(ShipperId shipperId) {
        super("Shipper not found: " + shipperId);
        this.shipperId = shipperId;
    }

    public ShipperId getShipperId() {
        return shipperId;
    }
}
