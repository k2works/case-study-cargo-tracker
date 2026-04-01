package com.example.cargotracker.shipper.application;

import com.example.cargotracker.shared.domain.model.ShipperId;

public class DuplicateShipperException extends RuntimeException {

    private final ShipperId existingShipperId;

    public DuplicateShipperException(ShipperId existingShipperId) {
        super("同一メールアドレスの荷主が既に登録されています: " + existingShipperId);
        this.existingShipperId = existingShipperId;
    }

    public ShipperId getExistingShipperId() {
        return existingShipperId;
    }
}
