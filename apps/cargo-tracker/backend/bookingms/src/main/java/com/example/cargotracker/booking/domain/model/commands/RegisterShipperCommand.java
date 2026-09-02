package com.example.cargotracker.booking.domain.model.commands;

import com.example.cargotracker.booking.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.booking.domain.model.valueobjects.Email;
import com.example.cargotracker.booking.domain.model.valueobjects.ShipperType;
import org.axonframework.modelling.annotation.TargetEntityId;

/** 荷主を登録する（UC02 / US02）。 */
public record RegisterShipperCommand(
        @TargetEntityId String shipperId,
        String name,
        ShipperType shipperType,
        Email email,
        String phone,
        String address,
        CorporateContract corporateContract) {
}
