package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.ports.ShipperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 荷主コマンドサービス（登録）
 */
@Service
public class ShipperCommandService {

    private final ShipperRepository shipperRepository;

    public ShipperCommandService(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    /**
     * 荷主登録コマンド
     */
    public record RegisterShipperCommand(String name, String email, String phone,
                                          String shipperType,
                                          String contractNumber, BigDecimal discountRate) {}

    /**
     * 荷主を登録する
     */
    @Transactional
    public Shipper register(RegisterShipperCommand command) {
        Shipper shipper;
        if ("CORPORATE".equalsIgnoreCase(command.shipperType())) {
            shipper = Shipper.createCorporate(
                    command.name(), command.email(), command.phone(),
                    command.contractNumber(), command.discountRate());
        } else {
            shipper = Shipper.createIndividual(command.name(), command.email(), command.phone());
        }
        return shipperRepository.save(shipper);
    }
}
