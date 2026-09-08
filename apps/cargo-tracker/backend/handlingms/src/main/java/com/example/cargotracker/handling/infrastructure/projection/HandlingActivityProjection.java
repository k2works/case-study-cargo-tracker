package com.example.cargotracker.handling.infrastructure.projection;

import com.example.cargotracker.handling.infrastructure.persistence.HandlingActivityMapper;
import com.example.cargotracker.handling.domain.model.events.ConsigneeConfirmationRecordedEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import java.time.Clock;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 荷役の記録の投影（US15 §受入基準 4）。
 *
 * <p><b>主キーは活動 ID</b>（クライアントが作る冪等キー）。追記の表なので、
 * 採番すると読み直すたびに同じ内容の行が積み上がる。</p>
 *
 * <p><b>取り消しは元の行を消さない。</b> 取り消した印を付けるだけで、
 * 現場で起きたことは履歴に残る（不変条件 7）。</p>
 */
@Component
public class HandlingActivityProjection {

    private final HandlingActivityMapper activities;
    private final Clock clock;

    public HandlingActivityProjection(HandlingActivityMapper activities, Clock clock) {
        this.activities = activities;
        this.clock = clock;
    }

    @EventHandler
    public void on(HandlingActivityRegisteredEvent event) {
        activities.insert(new HandlingActivityMapper.HandlingActivityRow(
                event.activityId(), event.trackingNumber(), event.bookingId(),
                event.handlingType(), event.unLocode(), event.voyageNumber(),
                // 荷受人の確認は引取（US16・IT10）が書く。
                null,
                event.offRoute(), event.operator(), event.completedAt(),
                false, null, null, null, clock.instant()));
    }

    /**
     * 荷受人の確認を書き足す（US16 §受入基準 2）。
     *
     * <p><b>記録の行はすでにある</b>（同じコマンドが先に
     * {@code HandlingActivityRegisteredEvent} を出している）。</p>
     */
    @EventHandler
    public void on(ConsigneeConfirmationRecordedEvent event) {
        activities.recordConsignee(event.activityId(), event.consigneeName(), clock.instant());
    }

    @EventHandler
    public void on(HandlingActivityVoidedEvent event) {
        // **運んでいた値を捨てない。** 契約は voidedBy を運んでいる（M13）。
        activities.markVoided(event.activityId(), event.reason(), event.voidedBy(),
                event.voidedAt(), clock.instant());
    }
}
