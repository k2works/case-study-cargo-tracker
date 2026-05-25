package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.ShipperRegisteredEvent;
import com.example.bookingms.infrastructure.repositories.mybatis.ShipperMapper;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 荷主 Read Model 更新用の EventHandler（US02 / US03）。
 *
 * <p>{@link ShipperRegisteredEvent} を受信して {@code shipper} テーブルに
 * 1 行 INSERT する。法人荷主の場合は contract_number / discount_rate も書き込む。</p>
 */
@Component
public class ShipperProjectionEventHandler {

    private final ShipperMapper shipperMapper;

    public ShipperProjectionEventHandler(ShipperMapper shipperMapper) {
        this.shipperMapper = shipperMapper;
    }

    @EventHandler
    public void on(ShipperRegisteredEvent event) {
        shipperMapper.insertShipper(
                event.shipperId(),
                event.shipperType().name(),
                event.name(),
                event.addressLine1(),
                event.addressLine2(),
                event.city(),
                event.countryCode(),
                event.postalCode(),
                event.email(),
                event.phone(),
                event.contractNumber(),
                event.discountRate()
        );
    }
}
