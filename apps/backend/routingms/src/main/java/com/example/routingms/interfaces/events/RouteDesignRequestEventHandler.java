package com.example.routingms.interfaces.events;

import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import com.example.shared.events.RouteDesignRequestedEvent;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 経路設計依頼イベントの受信ハンドラ（US06 / cross-service、ADR-0009）。
 *
 * <p>bookingms が発行する {@link RouteDesignRequestedEvent} を Kafka 経由で購読し、
 * 経路設計待ちリスト（route_design_request）に記録する。tracking モードでのイベント再処理に備え、
 * 同一 bookingId が既に登録済みなら何もしない（冪等）。</p>
 */
@Component
public class RouteDesignRequestEventHandler {

    private final RouteDesignRequestMapper mapper;

    public RouteDesignRequestEventHandler(RouteDesignRequestMapper mapper) {
        this.mapper = mapper;
    }

    @EventHandler
    public void on(RouteDesignRequestedEvent event) {
        if (mapper.findByBookingId(event.bookingId()) != null) {
            return;
        }
        mapper.insert(
                event.bookingId(),
                event.originUnlocode(),
                event.destinationUnlocode(),
                event.arrivalDeadline(),
                event.cargoType()
        );
    }
}
