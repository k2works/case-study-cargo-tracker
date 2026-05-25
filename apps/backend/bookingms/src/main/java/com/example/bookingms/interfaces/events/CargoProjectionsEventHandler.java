package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 貨物予約 Read Model 更新用の EventHandler（US04）。
 *
 * <p>{@link CargoBookedEvent} を受信して {@code cargo_summary} に 1 行 INSERT する。</p>
 */
@Component
public class CargoProjectionsEventHandler {

    private final CargoSummaryMapper cargoSummaryMapper;

    public CargoProjectionsEventHandler(CargoSummaryMapper cargoSummaryMapper) {
        this.cargoSummaryMapper = cargoSummaryMapper;
    }

    @EventHandler
    public void on(CargoBookedEvent event) {
        cargoSummaryMapper.insertCargoSummary(
                event.bookingId(),
                event.shipperId(),
                event.routeSpec().originUnlocode(),
                event.routeSpec().destinationUnlocode(),
                event.routeSpec().arrivalDeadline(),
                event.cargoSpec().cargoType().name(),
                event.cargoSpec().weightKg(),
                event.cargoSpec().dimensions().lengthCm(),
                event.cargoSpec().dimensions().widthCm(),
                event.cargoSpec().dimensions().heightCm(),
                event.cargoSpec().quantity(),
                event.cargoSpec().productName(),
                event.bookingStatus(),
                event.routingStatus()
        );
    }
}
