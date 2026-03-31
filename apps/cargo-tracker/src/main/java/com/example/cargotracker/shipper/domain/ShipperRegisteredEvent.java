package com.example.cargotracker.shipper.domain;

public record ShipperRegisteredEvent(ShipperId shipperId, CustomerCategory category) {
}
