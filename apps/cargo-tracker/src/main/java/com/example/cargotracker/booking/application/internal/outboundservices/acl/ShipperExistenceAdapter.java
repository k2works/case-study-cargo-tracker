package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.booking.application.internal.commandservices.ShipperNotFoundException;
import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistencePort;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ShipperExistencePort の ACL アダプター実装。
 * shipper コンテキストの ShipperRepository を経由して荷主の存在確認を行う。
 */
@Component
public class ShipperExistenceAdapter implements ShipperExistencePort {

    private final ShipperRepository shipperRepository;

    public ShipperExistenceAdapter(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    @Override
    public void verifyExists(UUID shipperId) {
        shipperRepository.findById(new ShipperId(shipperId))
                .orElseThrow(() -> new ShipperNotFoundException(shipperId.toString()));
    }
}
