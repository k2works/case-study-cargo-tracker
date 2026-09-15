package com.example.cargotracker.tracking.infrastructure.projection;

import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.tracking.infrastructure.persistence.ShipperOriginMapper;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 荷主の由来の写し（[ADR-0020] 決定 4）。
 *
 * <p>シミュレーションは本番の API で本物の業務データを作るので、何もしなければ
 * 追跡管理者の一覧（S40）にも荷役の作業一覧（S50・S54）にも並ぶ。US36 の継続実行は
 * これを<b>大量に</b>作るので、外さないと業務の一覧そのものが使えなくなる。</p>
 *
 * <p><b>印は荷主に付く</b>（ADR-0020 決定 4）。貨物は荷主から引き継ぐので、
 * 追跡を作るときに写しから解決する。</p>
 *
 * <p><b>処理の列を荷主ごとに分ける</b>（billingms の {@code ShipperContractProjection}
 * と同じ理由）。1 件の毒で無関係の荷主まで退避されると、被害が荷主の数だけ広がる。</p>
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
        // `null` は印が付く前に登録された荷主＝本物である（ADR-0020 決定 4）。
        shipperOrigins.upsert(event.shipperId(),
                Boolean.TRUE.equals(event.simulated()), clock.instant());
    }
}
