package com.example.trackingms.domain.model;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.commands.RegisterTrackingExceptionCommand;
import com.example.trackingms.domain.commands.ResolveTrackingExceptionCommand;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.shared.events.CargoDeliveredEvent;
import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingExceptionEscalatedEvent;
import com.example.trackingms.domain.events.TrackingExceptionRegisteredEvent;
import com.example.trackingms.domain.events.TrackingExceptionResolvedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.services.TransportStatusTransition;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 追跡活動集約（{@code TrackingActivity}、US14 / US17 / US19 / US20）。
 *
 * <p>貨物の追跡状況と例外管理を担う中核集約（domain-model.md）。IT5 では状態管理（NOT_RECEIVED〜DELIVERED、
 * MISROUTED）を、IT6 で例外管理（DELAY / DAMAGE / LOSS）を追加する。</p>
 *
 * <p>例外登録は許可状態（NOT_RECEIVED〜AWAITING_CLAIM）でのみ受理し、登録時に自動的に
 * {@code TransportStatusUpdatedEvent(現状態 → EXCEPTION)} と {@code TrackingExceptionRegisteredEvent} を
 * 順次発行する。LOSS 種別の場合は {@code TrackingExceptionEscalatedEvent} も同時に発行する。</p>
 */
@Aggregate
public class TrackingActivity {

    /**
     * 例外登録を許可する状態（plan.md IT6 設計）。MISROUTED / DELIVERED / EXCEPTION は拒否。
     */
    private static final Set<TransportStatus> EXCEPTION_REGISTRABLE_STATES = Set.of(
            TransportStatus.NOT_RECEIVED,
            TransportStatus.RECEIVED,
            TransportStatus.LOADED,
            TransportStatus.IN_TRANSIT,
            TransportStatus.UNLOADED,
            TransportStatus.AWAITING_CLAIM
    );

    @AggregateIdentifier
    private String trackingNumber;
    private String bookingId;
    private TransportStatus currentStatus;
    @SuppressWarnings("unused") // 誤配送フラグ。投影と例外管理（IT6）で利用
    private boolean misrouted;
    private final Map<String, TrackingException> exceptions = new HashMap<>();

    protected TrackingActivity() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public TrackingActivity(InitializeTrackingCommand command) {
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
        // IT7 T2 / ADR-0012：集約発火型で cross-service 配信。
        // DELIVERED への遷移は TransportStatusTransition が「DELIVERED → 任意」を拒否するため
        // 自然冪等。専用フラグや tracking_summary.delivered_published_at の SQL 更新は不要。
        if (command.toStatus() == TransportStatus.DELIVERED) {
            AggregateLifecycle.apply(new CargoDeliveredEvent(
                    this.trackingNumber,
                    this.bookingId,
                    command.occurredAt()
            ));
        }
    }

    /**
     * 追跡例外を登録する（US19 / US20）。
     *
     * <p>許可状態のみ受理し、内部で {@code TransportStatusUpdatedEvent(現状態 → EXCEPTION)} と
     * {@code TrackingExceptionRegisteredEvent} を順次発行する。LOSS 種別の場合は
     * {@code TrackingExceptionEscalatedEvent} も追加発行する。</p>
     */
    @CommandHandler
    public void handle(RegisterTrackingExceptionCommand command, Clock clock) {
        if (command.type() == null) {
            throw new IllegalArgumentException("type は必須です");
        }
        if (command.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt は必須です");
        }
        if (command.exceptionId() == null || command.exceptionId().isBlank()) {
            throw new IllegalArgumentException("exceptionId は必須です");
        }
        if (command.description() == null || command.description().isBlank()) {
            throw new IllegalArgumentException("description は必須です");
        }
        if (!EXCEPTION_REGISTRABLE_STATES.contains(this.currentStatus)) {
            throw new IllegalStateException(
                    "例外を登録できない状態です: " + this.currentStatus);
        }
        if (exceptions.containsKey(command.exceptionId())) {
            throw new IllegalArgumentException(
                    "同一 exceptionId が既に登録されています: " + command.exceptionId());
        }

        boolean escalated = command.type().isEscalationRequired();
        LocalDateTime now = LocalDateTime.now(clock);

        // 1) 状態を EXCEPTION に遷移
        AggregateLifecycle.apply(new TransportStatusUpdatedEvent(
                this.trackingNumber,
                this.currentStatus,
                TransportStatus.EXCEPTION,
                command.occurredUnlocode(),
                null,
                command.occurredAt(),
                "例外発生: " + command.type()
        ));

        // 2) 例外登録
        AggregateLifecycle.apply(new TrackingExceptionRegisteredEvent(
                this.trackingNumber,
                command.exceptionId(),
                command.type(),
                command.occurredAt(),
                command.occurredUnlocode(),
                command.description(),
                escalated
        ));

        // 3) LOSS のときは escalation も発行
        if (escalated) {
            AggregateLifecycle.apply(new TrackingExceptionEscalatedEvent(
                    this.trackingNumber,
                    command.exceptionId(),
                    command.type(),
                    command.occurredAt()
            ));
        }

        // suppress unused warning（clock を発火タイミング検証等で使う将来のため now を local 保持）
        if (now == null) {
            throw new IllegalStateException("Clock 注入失敗");
        }
    }

    /**
     * 追跡例外を解決済みに遷移させる（US19 / US20 受入基準 4/5）。
     */
    @CommandHandler
    public void handle(ResolveTrackingExceptionCommand command, Clock clock) {
        if (command.exceptionId() == null || command.exceptionId().isBlank()) {
            throw new IllegalArgumentException("exceptionId は必須です");
        }
        if (command.resolution() == null || command.resolution().isBlank()) {
            throw new IllegalArgumentException("resolution は必須です");
        }
        TrackingException target = exceptions.get(command.exceptionId());
        if (target == null) {
            throw new IllegalArgumentException(
                    "対象の例外が存在しません: " + command.exceptionId());
        }
        // 集約内で resolve を呼ぶことで状態遷移ガードを発動させる
        target.resolve(command.resolution(), LocalDateTime.now(clock));

        AggregateLifecycle.apply(new TrackingExceptionResolvedEvent(
                this.trackingNumber,
                command.exceptionId(),
                command.resolution(),
                target.getResolvedAt()
        ));
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

    @EventSourcingHandler
    public void on(TrackingExceptionRegisteredEvent event) {
        TrackingException te = new TrackingException(
                new TrackingExceptionId(event.exceptionId()),
                event.type(),
                event.occurredAt(),
                event.occurredUnlocode(),
                event.description()
        );
        exceptions.put(event.exceptionId(), te);
    }

    @EventSourcingHandler
    public void on(TrackingExceptionResolvedEvent event) {
        TrackingException te = exceptions.get(event.exceptionId());
        if (te != null) {
            // Event Sourcing 再生：集約状態を直接書き換える（resolve() の遷移ガードを通すと
            // 同イベント再適用で例外が出るため、replay 用の状態を直接設定）
            TrackingException replayed = TrackingException.replay(
                    te.getExceptionId(),
                    te.getType(),
                    te.getOccurredAt(),
                    te.getOccurredUnlocode(),
                    te.getDescription(),
                    com.example.trackingms.domain.model.ResponseStatus.RESOLVED,
                    event.resolution(),
                    event.resolvedAt()
            );
            exceptions.put(event.exceptionId(), replayed);
        }
    }

    @EventSourcingHandler
    @SuppressWarnings("unused") // 受信のみ。集約状態の追加変更は不要（escalated は TrackingException で持つ）
    public void on(TrackingExceptionEscalatedEvent event) {
        // 集約状態は TrackingExceptionRegisteredEvent.on で既に escalated=true を反映済み（type 経由）
        // 本イベントは TrackingNotificationEventHandler が拾って通知契機にする
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
