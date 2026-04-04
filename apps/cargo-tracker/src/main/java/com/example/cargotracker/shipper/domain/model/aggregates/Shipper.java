package com.example.cargotracker.shipper.domain.model.aggregates;

import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;

public class Shipper {

    private final ShipperId id;
    private final ShipperCode code;
    private final ShipperName name;
    private final Email email;
    private final Phone phone;
    private final ShipperType shipperType;

    public Shipper(
            ShipperId id,
            ShipperCode code,
            ShipperName name,
            Email email,
            Phone phone,
            ShipperType shipperType
    ) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (shipperType == null) {
            throw new IllegalArgumentException("shipperType must not be null");
        }
        this.id = id;
        this.code = code;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.shipperType = shipperType;
    }

    public ShipperId getId() {
        return id;
    }

    public ShipperCode getCode() {
        return code;
    }

    public ShipperName getName() {
        return name;
    }

    public Email getEmail() {
        return email;
    }

    public Phone getPhone() {
        return phone;
    }

    public ShipperType getShipperType() {
        return shipperType;
    }
}
