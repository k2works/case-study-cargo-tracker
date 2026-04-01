package com.example.cargotracker.shipper.application.internal.queryservices;

public class ShipperQueryNotFoundException extends RuntimeException {

    public ShipperQueryNotFoundException(String shipperId) {
        super("荷主が見つかりません: " + shipperId);
    }
}
