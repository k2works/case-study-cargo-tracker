package com.example.trackingms.domain.model;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * 追跡活動集約（{@code TrackingActivity}、US14 / IT5 タスク 1.3）。
 *
 * <p>貨物の追跡状況を表す中核集約（domain-model.md）。本コミットでは初期化のみを実装し、
 * {@code currentStatus} の遷移ロジック（US17 / IT5 タスク 2.x）と例外管理（US19 / US20 /
 * IT6 タスク）は後続イテレーションで追加する。</p>
 *
 * <p>初期化時は {@link TrackingNumber} の書式と {@code bookingId} の必須を検証し、
 * {@link TrackingInitializedEvent} を発行する。{@code currentStatus} は本イベントを適用すると
 * {@code TransportStatus.NOT_RECEIVED}（未受領）になる（受入条件）。</p>
 */
@Aggregate
public class TrackingActivity {

    @AggregateIdentifier
    private String trackingNumber;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持
    private String bookingId;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持。US17 で遷移を実装
    private TransportStatus currentStatus;

    protected TrackingActivity() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public TrackingActivity(InitializeTrackingCommand command) {
        // TrackingNumber コンストラクタが書式を検証（不正書式は IllegalArgumentException）
        TrackingNumber.of(command.trackingNumber());
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            throw new IllegalArgumentException("bookingId は必須です");
        }
        AggregateLifecycle.apply(new TrackingInitializedEvent(
                command.trackingNumber(),
                command.bookingId()
        ));
    }

    @EventSourcingHandler
    public void on(TrackingInitializedEvent event) {
        this.trackingNumber = event.trackingNumber();
        this.bookingId = event.bookingId();
        this.currentStatus = TransportStatus.NOT_RECEIVED;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
