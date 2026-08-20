package com.example.bookingms.application.internal;

import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.DiscountRate;
import com.example.bookingms.domain.model.ShipperType;

public record RegisterShipperCommand(
        ShipperType type, String name, String email, String address, String phone,
        ContractNumber contractNumber, DiscountRate discountRate) {

    /** 契約情報を伴わない登録。 */
    public RegisterShipperCommand(
            ShipperType type, String name, String email, String address, String phone) {
        this(type, name, email, address, phone, null, null);
    }
}
