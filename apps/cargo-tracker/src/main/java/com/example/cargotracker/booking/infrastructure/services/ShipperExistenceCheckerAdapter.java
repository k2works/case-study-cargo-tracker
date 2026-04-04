package com.example.cargotracker.booking.infrastructure.services;

import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistenceChecker;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import org.springframework.stereotype.Component;

/**
 * ShipperExistenceChecker の実装。
 * Shipper コンテキストの Repository を使用して荷主の存在を確認する。
 * Infrastructure 層に配置し、Booking の Application 層からは ACL ポート経由でアクセスする。
 */
@Component
public class ShipperExistenceCheckerAdapter implements ShipperExistenceChecker {

    private final ShipperRepository shipperRepository;

    public ShipperExistenceCheckerAdapter(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    @Override
    public boolean exists(ShipperId shipperId) {
        return shipperRepository.findById(shipperId).isPresent();
    }
}
