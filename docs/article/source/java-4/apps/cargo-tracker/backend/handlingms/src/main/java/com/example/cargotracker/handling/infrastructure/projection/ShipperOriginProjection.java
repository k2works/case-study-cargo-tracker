package com.example.cargotracker.handling.infrastructure.projection;

import com.example.cargotracker.handling.infrastructure.persistence.ShipperOriginMapper;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 荷主の由来の写し（[ADR-0020] 決定 4）。
 *
 * <p>US36 の継続実行はシミュレーションの貨物を<b>大量に</b>作る。外さないと、
 * 荷役作業員が朝いちばんに開くダッシュボード（S02）と作業一覧（S50）が
 * 架空の貨物で埋まり、一覧そのものが信用されなくなる。</p>
 *
 * <p><b>処理の列を荷主ごとに分ける</b>（1 件の毒で無関係の荷主まで退避しない）。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "shipperId")
@Component
public class ShipperOriginProjection {

    private final ShipperOriginMapper shipperOrigins;
    private final Clock clock;

    public ShipperOriginProjection(ShipperOriginMapper shipperOrigins, Clock clock) {
        this.shipperOrigins = shipperOrigins;
        this.clock = clock;
    }

    @EventHandler
    public void on(ShipperRegisteredEvent event) {
        // `null` は印が付く前に登録された荷主＝本物である。
        shipperOrigins.upsert(event.shipperId(),
                Boolean.TRUE.equals(event.simulated()), clock.instant());
    }
}
