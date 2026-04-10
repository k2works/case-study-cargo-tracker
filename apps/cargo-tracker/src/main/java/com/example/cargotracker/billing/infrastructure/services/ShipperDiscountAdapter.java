package com.example.cargotracker.billing.infrastructure.services;

import com.example.cargotracker.billing.application.internal.outboundservices.acl.ShipperDiscountPort;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ShipperDiscountAdapter implements ShipperDiscountPort {

    private final FindShipperQueryService findShipperQueryService;

    public ShipperDiscountAdapter(FindShipperQueryService findShipperQueryService) {
        this.findShipperQueryService = findShipperQueryService;
    }

    @Override
    public DiscountPolicy getDiscountPolicyForShipper(String shipperId) {
        return findShipperQueryService.findById(new ShipperId(UUID.fromString(shipperId)))
                .filter(shipper -> shipper instanceof CorporateShipper)
                .map(shipper -> (CorporateShipper) shipper)
                .map(corporate -> DiscountPolicy.corporate(corporate.getDiscountRate().value()))
                .orElse(DiscountPolicy.none());
    }
}
