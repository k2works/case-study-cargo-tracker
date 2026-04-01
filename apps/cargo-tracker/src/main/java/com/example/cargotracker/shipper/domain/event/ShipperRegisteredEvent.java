package com.example.cargotracker.shipper.domain.event;

import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shared.domain.model.ShipperId;

public record ShipperRegisteredEvent(ShipperId shipperId, CustomerCategory category) {
}
