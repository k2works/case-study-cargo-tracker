package com.example.trackingms.domain.model;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.services.TransportStatusTransition;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * 追跡活動集約（{@code TrackingActivity}、US14 / US17 / IT5 タスク 1.3 + 2.2）。
 *
 * <p>貨物の追跡状況を表す中核集約（domain-model.md）。初期化（{@link InitializeTrackingCommand}）に
 * 加えて、US17 で {@link UpdateTransportStatusCommand} による手動状態更新を受理する。</p>
 *
 * <p>状態遷移は {@link TransportStatusTransition} ドメインサービスが許可するもののみ受理し、
 * 不正遷移は {@code IllegalStateException} で拒否する。MISROUTED への遷移時は
 * {@link CargoMisroutedEvent} も同時に発行し、{@code misrouted} フラグを立てる。</p>
 */
@Aggregate
public class TrackingActivity {

    @AggregateIdentifier
    private String trackingNumber;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持
    private String bookingId;
    private TransportStatus currentStatus;
    @SuppressWarnings("unused") // 誤配送フラグ。投影と例外管理（IT6）で利用
    private boolean misrouted;

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

    @CommandHandler
    public void handle(UpdateTransportStatusCommand command, TransportStatusTransition transition) {
        if (command.toStatus() == null) {
            throw new IllegalArgumentException("toStatus は必須です");
        }
        if (command.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt は必須です");
        }
        if (!transition.canTransition(this.currentStatus, command.toStatus())) {
            throw new IllegalStateException(
                    "不正な状態遷移です: " + this.currentStatus + " → " + command.toStatus());
        }
        AggregateLifecycle.apply(new TransportStatusUpdatedEvent(
                this.trackingNumber,
                this.currentStatus,
                command.toStatus(),
                command.unlocode(),
                command.voyageNumber(),
                command.occurredAt(),
                command.description()
        ));
        if (command.toStatus() == TransportStatus.MISROUTED) {
            AggregateLifecycle.apply(new CargoMisroutedEvent(
                    this.trackingNumber,
                    command.unlocode(),
                    command.occurredAt()
            ));
        }
    }

    @EventSourcingHandler
    public void on(TrackingInitializedEvent event) {
        this.trackingNumber = event.trackingNumber();
        this.bookingId = event.bookingId();
        this.currentStatus = TransportStatus.NOT_RECEIVED;
        this.misrouted = false;
    }

    @EventSourcingHandler
    public void on(TransportStatusUpdatedEvent event) {
        this.currentStatus = event.toStatus();
    }

    @EventSourcingHandler
    public void on(CargoMisroutedEvent event) {
        this.misrouted = true;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
