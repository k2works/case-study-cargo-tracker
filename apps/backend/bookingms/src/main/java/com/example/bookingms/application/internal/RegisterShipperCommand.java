package com.example.bookingms.application.internal;

import com.example.bookingms.domain.model.ShipperType;

public record RegisterShipperCommand(
        ShipperType type, String name, String email, String address, String phone) {
}
