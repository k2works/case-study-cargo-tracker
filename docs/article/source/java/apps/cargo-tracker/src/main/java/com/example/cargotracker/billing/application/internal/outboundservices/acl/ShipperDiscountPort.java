package com.example.cargotracker.billing.application.internal.outboundservices.acl;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;

public interface ShipperDiscountPort {

    DiscountPolicy getDiscountPolicyForShipper(String shipperId);
}
