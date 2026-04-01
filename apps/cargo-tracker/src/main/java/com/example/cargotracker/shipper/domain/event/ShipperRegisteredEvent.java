package com.example.cargotracker.shipper.domain.event;

import com.example.cargotracker.shipper.domain.model.CustomerCategory;
import com.example.cargotracker.shipper.domain.model.ShipperId;

public record ShipperRegisteredEvent(ShipperId shipperId, CustomerCategory category) {
}
