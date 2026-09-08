package com.example.cargotracker.tracking.application.reaction;

import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.RevertTrackingCommand;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 荷役 → 追跡の反応（US15 §受入基準 4 / UC13）。
 *
 * <p><b>投影と別のパッケージに置く。</b> Processing Group はパッケージ名で分ける
 * （{@code @ProcessingGroup} は Axon 5 に無い）。同じにすると、投影のリプレイで
 * コマンドが再送され、貨物状態が二度進む。</p>
 *
 * <p><b>1 段で終わる。</b> 予約 → 追跡開始（ADR-0010）と違い、応答を待って次へ進む
 * 段がない。{@code process_state} は要らない——止まったかどうかは、荷役の記録と
 * 追跡の状態を突き合わせれば読める。</p>
 *
 * <p><b>判定は集約に任せる。</b> ここでは「進める」「戻す」を伝えるだけで、
 * 進める先（{@code TransportStatus#afterHandling}）も、進めてよいか
 * （遷移表・例外中）も集約が決める。</p>
 */
@Component
public class TrackingReactionHandler {

    private final CommandGateway commands;

    public TrackingReactionHandler(CommandGateway commands) {
        this.commands = commands;
    }

    @EventHandler
    public void on(HandlingActivityRegisteredEvent event) {
        commands.sendAndWait(new AdvanceTrackingCommand(event.trackingNumber(),
                event.activityId(), event.handlingType(), event.unLocode(), event.finalPort(),
                event.offRoute(), event.operator(), event.completedAt()), Void.class);
    }

    @EventHandler
    public void on(HandlingActivityVoidedEvent event) {
        commands.sendAndWait(new RevertTrackingCommand(event.trackingNumber(),
                event.activityId(), event.handlingType(), event.reason(), event.voidedBy(),
                event.voidedAt()), Void.class);
    }
}
