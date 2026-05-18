package com.example.cargotracker.trackingms.domain.model.aggregates;

import com.example.cargotracker.trackingms.domain.model.commands.InitializeTrackingCommand;
import com.example.cargotracker.trackingms.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.trackingms.domain.model.events.TrackingInitializedEvent;
import com.example.cargotracker.trackingms.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.trackingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.trackingms.domain.model.valueobjects.EventSource;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus;
import java.time.LocalDateTime;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.springframework.context.annotation.Profile;

/**
 * 追跡 Aggregate（US18）。
 *
 * <p>Axon Framework 5.1 の Event Sourcing パターンで実装する。
 * bookingms の {@code Cargo}・handlingms の {@code HandlingActivity} と同方針。</p>
 *
 * <p>主要不変条件:</p>
 *
 * <ul>
 *   <li>初期化は {@link TransportStatus#NOT_RECEIVED} 状態でのみ実行される</li>
 *   <li>状態遷移は {@code TransportStatusUpdatedEvent} 経由で行う（直接代入禁止）</li>
 *   <li>{@link TransportStatus#DELIVERED} に到達した時点で {@link #deliveredAt} を確定する
 *       （JWT 失効ロジック {@link com.example.cargotracker.trackingms.domain.model.services.TrackingTokenService}
 *       が参照する）</li>
 * </ul>
 *
 * <p>関連 ADR: ADR-0012 / ADR-0013</p>
 */
@EventSourced(idType = String.class, tagKey = "trackingNumber")
@Profile("!springboot-integration-test")
public final class TrackingActivity {

    private TrackingNumber trackingNumber;
    private String bookingId;
    private CargoItinerary itinerary;
    private Location origin;
    private Location destination;
    private TransportStatus currentStatus;
    private Location currentLocation;
    private LocalDateTime estimatedArrival;
    private LocalDateTime deliveredAt;
    private boolean misrouted;

    @EntityCreator
    public TrackingActivity() {
        // Axon が Event 再生で呼び出すデフォルトコンストラクタ。
    }

    /**
     * 追跡初期化（作成系コマンド）。
     *
     * <p>ADR-0007 推奨パターン: 作成系 Command は {@code static} メソッドとして実装し、
     * {@link EventAppender} を引数で受け取る。bookingms の {@code CargoTrackedEvent} を
     * 購読する Projection 経由で内部発行される想定。</p>
     */
    @CommandHandler
    public static String initialize(
            InitializeTrackingCommand command,
            EventAppender appender) {

        appender.append(new TrackingInitializedEvent(
                new TrackingNumber(command.trackingNumber()),
                command.bookingId(),
                command.itinerary(),
                command.origin(),
                command.destination(),
                command.estimatedArrival(),
                TransportStatus.NOT_RECEIVED));

        return command.trackingNumber();
    }

    /**
     * 貨物状態手動更新（US17 移管後・TI06）。
     *
     * <p>{@link com.example.cargotracker.trackingms.domain.model.valueobjects.EventSource#MANUAL}
     * として {@link TransportStatusUpdatedEvent} を発行する。</p>
     */
    @CommandHandler
    public void updateStatus(
            UpdateTransportStatusCommand command,
            EventAppender appender) {

        appender.append(new TransportStatusUpdatedEvent(
                this.trackingNumber,
                command.newStatus(),
                command.location(),
                command.updatedAt(),
                command.operatorId(),
                EventSource.MANUAL));
    }

    @EventSourcingHandler
    public void on(TrackingInitializedEvent event) {
        this.trackingNumber = event.trackingNumber();
        this.bookingId = event.bookingId();
        this.itinerary = event.itinerary();
        this.origin = event.origin();
        this.destination = event.destination();
        this.currentStatus = event.initialStatus();
        this.currentLocation = event.origin();
        this.estimatedArrival = event.estimatedArrival();
        this.misrouted = false;
        this.deliveredAt = null;
    }

    @EventSourcingHandler
    public void on(TransportStatusUpdatedEvent event) {
        this.currentStatus = event.newStatus();
        this.currentLocation = event.location();
        if (event.newStatus() == TransportStatus.DELIVERED) {
            this.deliveredAt = event.updatedAt();
        }
        if (event.newStatus() == TransportStatus.MISROUTED) {
            this.misrouted = true;
        }
    }

    public TrackingNumber getTrackingNumber() {
        return trackingNumber;
    }

    public String getBookingId() {
        return bookingId;
    }

    public CargoItinerary getItinerary() {
        return itinerary;
    }

    public Location getOrigin() {
        return origin;
    }

    public Location getDestination() {
        return destination;
    }

    public TransportStatus getCurrentStatus() {
        return currentStatus;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public LocalDateTime getEstimatedArrival() {
        return estimatedArrival;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public boolean isMisrouted() {
        return misrouted;
    }
}
