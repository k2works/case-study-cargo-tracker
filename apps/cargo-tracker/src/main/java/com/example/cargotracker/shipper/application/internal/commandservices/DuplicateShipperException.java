package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shared.domain.model.ShipperId;

import java.io.Serial;

public class DuplicateShipperException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ShipperId existingShipperId;

    public DuplicateShipperException(ShipperId existingShipperId) {
        super("同一メールアドレスの荷主が既に登録されています: " + existingShipperId);
        this.existingShipperId = existingShipperId;
    }

    public ShipperId getExistingShipperId() {
        return existingShipperId;
    }
}
