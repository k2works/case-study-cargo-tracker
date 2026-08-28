package com.example.bookingms.domain.model.commands;

import com.example.bookingms.domain.model.valueobjects.CorporateContract;
import com.example.bookingms.domain.model.valueobjects.ShipperType;

public record RegisterShipperCommand(
        ShipperType type, String name, String email, String address, String phone,
        CorporateContract contract) {

    /** 契約情報を伴わない登録。 */
    public RegisterShipperCommand(
            ShipperType type, String name, String email, String address, String phone) {
        this(type, name, email, address, phone, null);
    }
}
