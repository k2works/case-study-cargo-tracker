package com.example.bookingms.domain.model.commands;

import com.example.bookingms.domain.model.valueobjects.CorporateContract;
import com.example.bookingms.domain.model.valueobjects.ShipperType;

public record RegisterShipperCommand(
        ShipperType type, String name, String email, String address, String phone,
        CorporateContract contract,
        /** シミュレーションが作る荷主か（[ADR-030] 決定 3）。荷主コードの帯が変わる。 */
        boolean simulated) {

    /** 契約情報を伴わない登録。 */
    public RegisterShipperCommand(
            ShipperType type, String name, String email, String address, String phone) {
        this(type, name, email, address, phone, null, false);
    }

    /** 実業務の登録。 */
    public RegisterShipperCommand(
            ShipperType type, String name, String email, String address, String phone,
            CorporateContract contract) {
        this(type, name, email, address, phone, contract, false);
    }
}
