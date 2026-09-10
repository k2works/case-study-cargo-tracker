package com.example.cargotracker.tracking.application.reaction;

import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.shared.contract.event.CustomsStatusChangedEvent;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.RegisterTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.ResolveTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.RevertTrackingCommand;
import com.example.cargotracker.tracking.domain.model.entities.TrackingException;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomsHoldStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
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

    /**
     * 通関が留置になったら税関保留を起票し、留置から出たら解決する（US29 §受入基準 5）。
     *
     * <p><b>起票だけでは足りない。</b> 例外中の貨物は荷役を預かって適用しないので
     * （IT11 引き継ぎ枠 B）、解決しないと<b>通関済にしても引取が届かない</b>。
     * 「その状態にする経路を足したら、既存の経路の後段を数えて写す」——ここでは
     * 起票（{@code registerException}）の後段が解決（{@code resolveException}）である。</p>
     *
     * <p><b>識別子は申告から導く。</b> 採番すると、留置が再配送されるたびに新しい
     * 例外ができる。同じ識別子なら、起票は 2 度目を無視し、解決も 2 度目を無視する。</p>
     *
     * <p><b>判定は列挙が答える。</b> どの状態が起票で、どの状態が解決かを
     * ここに {@code if} で書くと、状態が増えたときに書き換える場所が散らばる。</p>
     */
    @EventHandler
    public void on(CustomsStatusChangedEvent event) {
        String exceptionId = TrackingException.customsHoldIdFor(event.declarationNumber());
        CustomsHoldStatus status = CustomsHoldStatus.of(event.status());
        if (status.raisesHold()) {
            commands.sendAndWait(new RegisterTrackingExceptionCommand(event.trackingNumber(),
                    exceptionId, ExceptionType.CUSTOMS_HOLD, event.changedAt(), null,
                    "通関が留置になりました（申告番号 " + event.declarationNumber()
                            + "）: " + event.reason(), event.changedBy()), Void.class);
            return;
        }
        if (status.resolvesHold()) {
            commands.sendAndWait(new ResolveTrackingExceptionCommand(event.trackingNumber(),
                    exceptionId,
                    "通関状態が " + status.label() + " になりました: " + event.reason(),
                    event.changedBy()), Void.class);
        }
    }
}
