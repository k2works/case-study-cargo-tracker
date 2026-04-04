package com.example.cargotracker.shipper.interfaces.rest.transform;

import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.interfaces.rest.dto.ShipperResponse;
import org.springframework.stereotype.Component;

@Component
public class ShipperAssembler {

    public ShipperResponse toResponse(Shipper shipper) {
        String contractNumber = null;
        java.math.BigDecimal discountRate = null;

        if (shipper instanceof CorporateShipper corporateShipper) {
            contractNumber = corporateShipper.getContractNumber().value();
            discountRate = corporateShipper.getDiscountRate().value();
        }

        return new ShipperResponse(
                shipper.getId().toString(),
                shipper.getCode().value(),
                shipper.getName().value(),
                shipper.getEmail().value(),
                shipper.getPhone() == null ? null : shipper.getPhone().value(),
                shipper.getShipperType().name(),
                contractNumber,
                discountRate
        );
    }
}
